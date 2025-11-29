package com.price.market;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.YieldingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.EventHandlerGroup;
import com.lmax.disruptor.dsl.ProducerType;
import com.price.common.Configuration;
import com.price.event.MarketDataEvent;
import com.price.storage.CandleProcessor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.concurrent.Executors;

@Slf4j
public class MarketDataProcessor implements AutoCloseable {
    public final Instrument instrument;
    private final Disruptor<MarketDataEvent> disruptor;
    private final RingBuffer<MarketDataEvent> ringBuffer;

    public MarketDataProcessor(Instrument instrument, CandleProcessor candleProcessor, Configuration configuration) {
        this.instrument = instrument;

        disruptor = new Disruptor<>(
                MarketDataEvent::new,
                configuration.getDisruptorBufferSize(),
                Executors.defaultThreadFactory(),
                ProducerType.MULTI,
                new YieldingWaitStrategy()
        );

        EventHandlerGroup<MarketDataEvent> group = null;

        for (int timeframe : instrument.timeframes()) {
            CandleAggregator aggregator = new CandleAggregator(
                    instrument.fullName(),
                    timeframe,
                    candleProcessor
            );
            if (group == null) {
                group = disruptor.handleEventsWith(aggregator);
            } else {
                group = group.then(aggregator);
            }
        }
        this.ringBuffer = disruptor.getRingBuffer();
    }

    public void start() {
        disruptor.start();
    }

    public void handlePriceEvent(long timestamp, double price, long volume) {
        log.debug("Received market data event: {} {} {}", timestamp, price, volume);
        long sequence = ringBuffer.next();
        try {
            MarketDataEvent event = ringBuffer.get(sequence);
            event.type(MarketDataEvent.Type.DATA);
            event.timestamp(timestamp);
            event.price(price);
            event.volume(volume);
        } finally {
            ringBuffer.publish(sequence);
        }
    }

    public void handleTimerEvent(long timestamp) {
        long sequence = ringBuffer.next();
        try {
            MarketDataEvent event = ringBuffer.get(sequence);
            event.timestamp(timestamp);
            event.type(MarketDataEvent.Type.TIMER);
        } finally {
            ringBuffer.publish(sequence);
        }
    }

    @Override
    public void close() throws IOException {
        disruptor.halt();
    }
}
