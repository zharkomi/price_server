package com.price.stream.market;

import com.lmax.disruptor.EventHandler;
import com.price.stream.event.buffer.MarketDataEvent;

public class ClientNotifier implements EventHandler<MarketDataEvent> {

    private final MarketDataHandler marketDataHandler;

    public ClientNotifier(MarketDataHandler marketDataHandler) {
        this.marketDataHandler = marketDataHandler;
    }

    @Override
    public void onEvent(MarketDataEvent event, long sequence, boolean endOfBatch) throws Exception {
        if (event.type() == MarketDataEvent.Type.TIMER) {
            marketDataHandler.instrumentProcessed();
        }
    }
}
