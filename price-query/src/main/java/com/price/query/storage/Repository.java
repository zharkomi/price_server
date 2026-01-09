package com.price.query.storage;

import java.util.List;

public interface Repository {
    List<Candle> queryCandles(String instrument, int timeframeMs, long fromTimestamp, long toTimestamp) throws Exception;
}
