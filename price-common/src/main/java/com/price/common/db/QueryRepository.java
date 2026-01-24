package com.price.common.db;

import java.util.List;

public interface QueryRepository extends AutoCloseable {
    List<Candle> queryCandles(String instrument, int timeframeMs, long fromTimestamp, long toTimestamp) throws Exception;
}
