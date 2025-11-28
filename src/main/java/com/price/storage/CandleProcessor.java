package com.price.storage;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CandleProcessor {
    public void handleEvent(CandleEvent candleEvent) {
        log.info("Processed candle event: {}", candleEvent);
    }
}
