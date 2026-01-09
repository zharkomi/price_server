package com.price.query.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Repository
public class ClickHouseRepository implements com.price.query.storage.Repository {
    private static final String DATABASE_NAME = "prices_db";

    public static final String QUERY_SELECT_CANDLES = "SELECT time, open, high, low, close, volume " +
            "FROM trade_candles " +
            "WHERE instrument = ? AND timeframe_ms = ? AND time >= ? AND time < ? " +
            "ORDER BY time ASC";

    private final String url;
    private final String user;
    private final String password;
    private Connection connection;

    public ClickHouseRepository(@Value("${ps.clickhouse.url}") String url,
                                @Value("${ps.clickhouse.user}") String user,
                                @Value("${ps.clickhouse.password}") String password) {
        // Disable compression to avoid LZ4 issues
        this.url = url + "/" + DATABASE_NAME + "?compress=0";
        this.user = user;
        this.password = password;
        log.info("ClickHouseRepository configured for database: {}", DATABASE_NAME);
    }

    private Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(url, user, password);
            log.info("Connected to ClickHouse database: {}", DATABASE_NAME);
        }
        return connection;
    }

    @Override
    public List<Candle> queryCandles(String instrument, int timeframeMs, long fromTimestamp, long toTimestamp) throws Exception {
        List<Candle> candles = new ArrayList<>();
        try (PreparedStatement stmt = getConnection().prepareStatement(QUERY_SELECT_CANDLES)) {
            stmt.setString(1, instrument);
            stmt.setInt(2, timeframeMs);
            stmt.setLong(3, fromTimestamp);
            stmt.setLong(4, toTimestamp);

            log.debug("Executing query: {} with params: instrument={}, timeframeMs={}, from={}, to={}",
                    QUERY_SELECT_CANDLES, instrument, timeframeMs, fromTimestamp, toTimestamp);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Candle candle = new Candle(
                            instrument,
                            timeframeMs,
                            rs.getLong("time"),
                            rs.getDouble("open"),
                            rs.getDouble("high"),
                            rs.getDouble("low"),
                            rs.getDouble("close"),
                            rs.getDouble("volume")
                    );
                    candles.add(candle);
                }
            }
        } catch (SQLException e) {
            log.error("Failed to query candles for instrument {}", instrument, e);
            throw e;
        }

        log.info("Queried {} candles for instrument {} timeframe {}ms from {} to {}",
                candles.size(), instrument, timeframeMs, fromTimestamp, toTimestamp);
        return candles;
    }
}
