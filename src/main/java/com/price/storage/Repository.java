package com.price.storage;

import com.lmax.disruptor.EventHandler;
import com.price.event.CandleEvent;

import java.util.List;

public interface Repository extends EventHandler<CandleEvent>, AutoCloseable {
    void onEvent(CandleEvent event, long sequence, boolean endOfBatch) throws Exception;

    List<CandleEvent> queryCandles(String instrument, int timeframeMs, long fromTimestamp, long toTimestamp) throws Exception;
}
