CREATE TABLE prices_db.trade_candles
(
    instrument       LowCardinality(String),
    timeframe_ms     UInt32,
    time             UInt64,
    open             Float64,
    high             Float64,
    low              Float64,
    close            Float64,
    volume           Float64
)
ENGINE = ReplacingMergeTree()
PARTITION BY (instrument, timeframe_ms, toDate(time / 1000))
ORDER BY (instrument, timeframe_ms, time)
SETTINGS index_granularity = 8192;
