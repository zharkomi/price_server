package com.price.stream.market;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.YieldingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.EventHandlerGroup;
import com.lmax.disruptor.dsl.ProducerType;
import com.price.common.config.PriceConfiguration;
import com.price.common.config.Instrument;
import com.price.common.source.PriceEventHandler;
import com.price.stream.event.buffer.MarketDataEvent;
import com.price.stream.service.ClientSubscriptionProcessor;
import com.price.stream.storage.CandlePersistenceProcessor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

@Slf4j
public class InstrumentDataProcessor implements PriceEventHandler, AutoCloseable {
    private final Instrument instrument;
    private final Disruptor<MarketDataEvent> disruptor;
    private final RingBuffer<MarketDataEvent> ringBuffer;
    private final Map<Integer, CandleAggregator> aggregators;

    public InstrumentDataProcessor(MarketDataHandler marketDataHandler, Instrument instrument, List<CandlePersistenceProcessor> candleProcessors, PriceConfiguration configuration) {
        this.instrument = instrument;

        disruptor = new Disruptor<>(
                MarketDataEvent::new,
                configuration.marketDataBufferSize(),
                Executors.defaultThreadFactory(),
                ProducerType.MULTI,
                new YieldingWaitStrategy()
        );

        EventHandlerGroup<MarketDataEvent> group = null;

        aggregators = new java.util.HashMap<>();
        for (int timeframe : instrument.timeframes()) {
            CandleAggregator aggregator = new CandleAggregator(
                    instrument,
                    timeframe,
                    candleProcessors
            );
            aggregators.put(timeframe, aggregator);
            if (group == null) {
                group = disruptor.handleEventsWith(aggregator);
            } else {
                group = group.then(aggregator);
            }
        }
        if (group == null) {
            throw new IllegalArgumentException("At least one timeframe must be added for instrument: " + instrument.name());
        }
        marketDataHandler.appendHandlers(group);
        this.ringBuffer = disruptor.getRingBuffer();
    }

    public void start() {
        disruptor.start();
    }

    @Override
    public Instrument getInstrument() {
        return instrument;
    }

    @Override
    public void handlePriceEvent(long timestamp, double price, long volume) {
        this.instrument.marketEvents().accumulate(1);
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

    public void subscribe(int timeframe, ClientSubscriptionProcessor clientSubscriptionProcessor) {
        handle(timeframe, a -> a.subscribe(clientSubscriptionProcessor));
    }

    public void unsubscribe(int timeframe, ClientSubscriptionProcessor clientSubscriptionProcessor) {
        handle(timeframe, a -> a.unsubscribe(clientSubscriptionProcessor));
    }

    private void handle(int timeframe, Consumer<CandleAggregator> command) {
        CandleAggregator aggregator = aggregators.get(timeframe);
        if (aggregator != null) {
            command.accept(aggregator);
        } else {
            log.error("CandleAggregator not found for timeframe: {} on instrument: {}", timeframe, instrument.name());
        }
    }
}
