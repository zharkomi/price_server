package com.price.stream.storage;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.YieldingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.price.common.storage.SaveRepository;
import com.price.stream.common.CandleProcessor;
import com.price.stream.common.SubscriptionKey;
import com.price.common.config.PriceConfiguration;
import com.price.common.storage.CandleEvent;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executors;

@Slf4j
public class CandlePersistenceProcessor implements CandleProcessor, AutoCloseable {
    private final Disruptor<CandleEvent> disruptor;
    private final RingBuffer<CandleEvent> ringBuffer;

    public CandlePersistenceProcessor(SaveRepository repository, PriceConfiguration configuration) {
        disruptor = new Disruptor<>(
                CandleEvent::new,
                configuration.disruptorBufferSize(),
                Executors.defaultThreadFactory(),
                ProducerType.MULTI,
                new YieldingWaitStrategy()
        );

        // Repository implements the EventHandler interface
        disruptor.handleEventsWith(repository);

        this.ringBuffer = disruptor.getRingBuffer();
    }

    public void start() {
        disruptor.start();
        log.info("CandleProcessor started");
    }

    public void handleCandleEvent(SubscriptionKey subscriptionKey, long time,
                                  double open, double high, double low, double close, long volume) {
        long sequence = ringBuffer.next();
        try {
            CandleEvent event = ringBuffer.get(sequence);
            event.instrument(subscriptionKey.instrument());
            event.timeframeMs(subscriptionKey.timeframe());
            event.time(time);
            event.open(open);
            event.high(high);
            event.low(low);
            event.close(close);
            event.volume(volume);
            log.debug("Published candle event: {}", event);
        } finally {
            ringBuffer.publish(sequence);
        }
    }

    @Override
    public void close() throws Exception {
        log.info("Shutting down CandleProcessor");
        disruptor.halt();
    }
}
