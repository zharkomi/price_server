package com.price.db;

import com.lmax.disruptor.EventHandler;
import com.price.common.config.DataBase;
import com.price.common.storage.CandleEvent;
import com.price.common.storage.SaveRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.*;

@Slf4j
public class ClickHouseRepository implements SaveRepository, EventHandler<CandleEvent>, AutoCloseable {
    private static final String DATABASE_NAME = "prices_db";
    private static final String SCHEMA_RESOURCE = "/clickhouse/schema.sql";

    public static final String QUERY_CREATE_DB = "CREATE DATABASE " + DATABASE_NAME;
    public static final String QUERY_INSERT_PRICES = "INSERT INTO trade_candles " +
            "(instrument, timeframe_ms, time, open, high, low, close, volume) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";


    private final Connection connection;
    private final PreparedStatement insertStatement;

    public ClickHouseRepository(DataBase configuration) {
        try {
            String baseUrl = configuration.url();
            String user = configuration.user();
            String password = configuration.password();

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
