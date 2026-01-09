package com.price.market;

import com.lmax.disruptor.EventHandler;
import com.price.event.buffer.MarketDataEvent;
import com.price.service.SubscriptionProcessor;

import java.util.concurrent.CopyOnWriteArrayList;

public class ClientNotifier implements EventHandler<MarketDataEvent> {

    CopyOnWriteArrayList<SubscriptionProcessor> subscriptionProcessors = new CopyOnWriteArrayList<>();

    @Override
    public void onEvent(MarketDataEvent event, long sequence, boolean endOfBatch) throws Exception {
        if (event.type() == MarketDataEvent.Type.TIMER) {
            subscriptionProcessors.forEach(SubscriptionProcessor::instrumentProcessed);
        }
    }

    public void add(SubscriptionProcessor subscriptionProcessor) {
        subscriptionProcessors.add(subscriptionProcessor);
    }

    public void remove(SubscriptionProcessor subscriptionProcessor) {
        subscriptionProcessors.remove(subscriptionProcessor);
    }
}
