package com.price.stream.storage;

import com.lmax.disruptor.EventHandler;
import com.price.stream.event.buffer.CandleEvent;

public interface Repository extends EventHandler<CandleEvent>, AutoCloseable {
    void onEvent(CandleEvent event, long sequence, boolean endOfBatch) throws Exception;
}
