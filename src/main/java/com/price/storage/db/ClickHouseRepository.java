package com.price.storage.db;

import com.lmax.disruptor.EventHandler;
import com.price.common.Configuration;
import com.price.event.CandleEvent;
import com.price.storage.Repository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class ClickHouseRepository implements Repository, EventHandler<CandleEvent>, AutoCloseable {
    private static final String DATABASE_NAME = "prices_db";
    private static final String SCHEMA_RESOURCE = "/clickhouse/schema.sql";

    public static final String QUERY_CREATE_DB = "CREATE DATABASE " + DATABASE_NAME;
    public static final String QUERY_INSERT_PRICES = "INSERT INTO trade_candles " +
            "(instrument, timeframe_ms, time, open, high, low, close, volume) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    public static final String QUERY_SELECT_CANDLES = "SELECT time, open, high, low, close, volume " +
            "FROM trade_candles " +
            "WHERE instrument = ? AND timeframe_ms = ? AND time >= ? AND time <= ? " +
            "ORDER BY time ASC";

    private final Connection connection;
    private final PreparedStatement insertStatement;

    public ClickHouseRepository(Configuration configuration) {
        try {
            String baseUrl = configuration.clickhouseUrl;
            String user = configuration.clickhouseUser;
            String password = configuration.clickhousePassword;

            // Disable compression to avoid LZ4 issues
            String urlParams = baseUrl.contains("?") ? "&compress=0" : "?compress=0";
            String url = baseUrl + urlParams;

            log.info("Initializing ClickHouse repository at {}", url);

            // First connect without specifying a database to check/create it
            Connection tempConnection = DriverManager.getConnection(url, user, password);
            createDatabaseIfNotExists(tempConnection);
            tempConnection.close();

            // Now connect to our database
            String dbUrl = url + "/" + DATABASE_NAME;
            this.connection = DriverManager.getConnection(dbUrl, user, password);

            // Ensure auto-commit is enabled
            connection.setAutoCommit(true);
            log.info("Connected to ClickHouse database: {} (autoCommit={})", DATABASE_NAME, connection.getAutoCommit());

            // Create table if it doesn't exist
            createTableIfNotExists();

            // Prepare insert statement
            this.insertStatement = connection.prepareStatement(QUERY_INSERT_PRICES);

            log.info("ClickHouse repository initialized successfully");
        } catch (SQLException e) {
            log.error("Failed to initialize ClickHouse repository", e);
            throw new RuntimeException("Failed to initialize ClickHouse repository", e);
        }
    }

    private void createDatabaseIfNotExists(Connection conn) throws SQLException {
        String checkQuery = "SELECT count() FROM system.databases WHERE name = '" + DATABASE_NAME + "'";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(checkQuery)) {
            if (rs.next() && rs.getInt(1) == 0) {
                log.info("Database {} does not exist, creating it", DATABASE_NAME);
                stmt.execute(QUERY_CREATE_DB);
                log.info("Database {} created successfully", DATABASE_NAME);
            } else {
                log.info("Database {} already exists", DATABASE_NAME);
            }
        }
    }

    private void createTableIfNotExists() throws SQLException {
        String checkQuery = "SELECT count() FROM system.tables WHERE database = '" + DATABASE_NAME + "' AND name = 'trade_candles'";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(checkQuery)) {
            if (rs.next() && rs.getInt(1) == 0) {
                log.info("Table trade_candles does not exist, creating it");
                String schema = loadSchemaFromResource();
                stmt.execute(schema);
                log.info("Table trade_candles created successfully");
            } else {
                log.info("Table trade_candles already exists");
            }
        }
    }

    private String loadSchemaFromResource() {
        try (InputStream is = getClass().getResourceAsStream(SCHEMA_RESOURCE)) {
            if (is == null) {
                throw new RuntimeException("Schema resource not found: " + SCHEMA_RESOURCE);
            }
            return IOUtils.toString(is, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load schema from resource: " + SCHEMA_RESOURCE, e);
        }
    }

    @Override
    public void onEvent(CandleEvent event, long sequence, boolean endOfBatch) throws Exception {
        log.info("Processing candle event: {}", event);
        try {
            insertStatement.setString(1, event.instrument());
            insertStatement.setInt(2, event.timeframeMs());
            insertStatement.setLong(3, event.time());
            insertStatement.setDouble(4, event.open());
            insertStatement.setDouble(5, event.high());
            insertStatement.setDouble(6, event.low());
            insertStatement.setDouble(7, event.close());
            insertStatement.setLong(8, event.volume());
            insertStatement.addBatch();

            if (endOfBatch) {
                int[] results = insertStatement.executeBatch();
                log.debug("Batch inserted {} candles at sequence {}", results.length, sequence);
            }
        } catch (SQLException e) {
            log.error("Failed to insert candle event", e);
            throw e;
        }
    }

    @Override
    public List<CandleEvent> queryCandles(String instrument, int timeframeMs, long fromTimestamp, long toTimestamp) throws Exception {
        List<CandleEvent> candles = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(QUERY_SELECT_CANDLES)) {
            stmt.setString(1, instrument);
            stmt.setInt(2, timeframeMs);
            stmt.setLong(3, fromTimestamp);
            stmt.setLong(4, toTimestamp);

            log.info("Executing query: {} with params: instrument={}, timeframeMs={}, from={}, to={}",
                    QUERY_SELECT_CANDLES, instrument, timeframeMs, fromTimestamp, toTimestamp);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    CandleEvent candle = new CandleEvent();
                    candle.instrument(instrument);
                    candle.timeframeMs(timeframeMs);
                    candle.time(rs.getLong("time"));
                    candle.open(rs.getDouble("open"));
                    candle.high(rs.getDouble("high"));
                    candle.low(rs.getDouble("low"));
                    candle.close(rs.getDouble("close"));
                    candle.volume(rs.getLong("volume"));
                    candles.add(candle);
                    log.info("Retrieved candle: time={}, open={}, high={}, low={}, close={}, volume={}",
                            candle.time(), candle.open(), candle.high(), candle.low(), candle.close(), candle.volume());
                }
            }
        } catch (SQLException e) {
            log.error("Failed to query candles", e);
            throw e;
        }

        log.info("Queried {} candles for instrument {} timeframe {}ms from {} to {}",
                candles.size(), instrument, timeframeMs, fromTimestamp, toTimestamp);
        return candles;
    }

    @Override
    public void close() throws Exception {
        log.info("Closing ClickHouse repository");
        if (insertStatement != null) {
            try {
                insertStatement.executeBatch();
                insertStatement.close();
            } catch (SQLException e) {
                log.error("Error closing insert statement", e);
            }
        }
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                log.error("Error closing connection", e);
            }
        }
        log.info("ClickHouse repository closed");
    }
}
