package com.price.common.db;

import com.lmax.disruptor.EventHandler;

public interface SaveRepository extends EventHandler<CandleEvent>, AutoCloseable {
    void onEvent(CandleEvent event, long sequence, boolean endOfBatch) throws Exception;
}
