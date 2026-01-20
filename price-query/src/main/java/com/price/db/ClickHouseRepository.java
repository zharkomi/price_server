package com.price.db;

import com.price.common.config.DataBase;
import com.price.common.storage.Candle;
import com.price.common.storage.QueryRepository;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Repository
public class ClickHouseRepository implements QueryRepository {
    private static final String DATABASE_NAME = "prices_db";

    public static final String QUERY_SELECT_CANDLES = "SELECT time, open, high, low, close, volume " +
            "FROM " + DATABASE_NAME + ".trade_candles " +
            "WHERE instrument = ? AND timeframe_ms = ? " +
            "  AND time >= ? " +
            "  AND time < ? " +
            "ORDER BY time ASC";

    private final HikariDataSource dataSource;

    public ClickHouseRepository(DataBase dataBase) {
        log.info("ClickHouseRepository configured for database: {}", DATABASE_NAME);

        // Append database name to URL (same pattern as price-stream)
        String baseUrl = dataBase.url();
        String dbUrl = baseUrl + "/" + DATABASE_NAME;

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(dbUrl);
        config.setUsername(dataBase.user());
        config.setPassword(dataBase.password());
        config.setDriverClassName("com.clickhouse.jdbc.ClickHouseDriver");
        config.setMaximumPoolSize(10); // Example size
        config.setMinimumIdle(2);     // Example idle connections
        config.setIdleTimeout(600000); // 10 minutes
        config.setConnectionTimeout(30000); // 30 seconds

        this.dataSource = new HikariDataSource(config);
    }

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
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

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            log.info("ClickHouse HikariDataSource closed.");
        }
    }
}
