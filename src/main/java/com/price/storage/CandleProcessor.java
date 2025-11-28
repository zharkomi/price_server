package com.price.storage;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CandleProcessor implements AutoCloseable {
    public void handleEvent(CandleEvent candleEvent) {
        log.info("Processed candle event: {}", candleEvent);
    }

    @Override
    public void close() throws Exception {

    }
}
