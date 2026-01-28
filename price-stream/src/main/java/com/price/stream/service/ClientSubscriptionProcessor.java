package com.price.stream.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.YieldingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.price.common.db.CandleEvent;
import com.price.stream.common.CandleProcessor;
import com.price.stream.common.SubscriptionKey;
import com.price.stream.market.MarketDataHandler;
import com.price.stream.market.InstrumentDataProcessor;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

@Slf4j
public class ClientSubscriptionProcessor implements CandleProcessor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SocketChannel channel;
    private final MarketDataHandler marketDataHandler;
    private final Set<SubscriptionKey> subscriptions = ConcurrentHashMap.newKeySet();

    private final Disruptor<CandleEvent> disruptor;
    private final RingBuffer<CandleEvent> ringBuffer;

    public ClientSubscriptionProcessor(SocketChannel channel, MarketDataHandler marketDataHandler) {
        this.channel = channel;
        this.marketDataHandler = marketDataHandler;

        this.disruptor = new Disruptor<>(
                CandleEvent::new,
                1024,
                Executors.defaultThreadFactory(),
                ProducerType.MULTI,
                new YieldingWaitStrategy()
        );

        disruptor.handleEventsWith(this::sendToChannel);
        this.ringBuffer = disruptor.getRingBuffer();
    }

    public void start() {
        disruptor.start();
        this.marketDataHandler.register(this);
    }

    public synchronized void subscribe(String instrument, int timeframe) {
        SubscriptionKey key = new SubscriptionKey(instrument, timeframe);
        if (subscriptions.add(key)) {
            InstrumentDataProcessor processor = marketDataHandler.get(instrument);
            if (processor != null) {
                processor.subscribe(timeframe, this);
                log.info("Subscribed to {} with timeframe {}", instrument, timeframe);
            } else {
                log.warn("No processor found for instrument: {}", instrument);
                subscriptions.remove(key);
            }
        }
    }

    public synchronized void unsubscribe(String instrument, int timeframe) {
        SubscriptionKey key = new SubscriptionKey(instrument, timeframe);
        if (subscriptions.remove(key)) {
            InstrumentDataProcessor processor = marketDataHandler.get(instrument);
            if (processor != null) {
                processor.unsubscribe(timeframe, this);
                log.info("Unsubscribed from {} with timeframe {}", instrument, timeframe);
            }
        }
    }

    public synchronized void stop() {
        for (SubscriptionKey key : new ArrayList<>(subscriptions)) {
            unsubscribe(key.instrument(), key.timeframe());
        }
        marketDataHandler.deregister(this);
        disruptor.halt();
    }

    @Override
    public void handleCandleEvent(SubscriptionKey subscriptionKey, long time, double open, double high, double low, double close, long volume) {
        if (subscriptions.contains(subscriptionKey)) {
            log.debug("Processing candle event for {}", subscriptionKey);
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
            } finally {
                ringBuffer.publish(sequence);
            }
        }
    }

    public void timeFrameProcessed() {
        // No aggregation - events sent directly in handleCandleEvent
    }

    private void sendToChannel(CandleEvent event, long sequence, boolean endOfBatch) {
        try {
            String json = MAPPER.writeValueAsString(event);
            channel.writeAndFlush(new TextWebSocketFrame(json));
        } catch (Exception e) {
            log.error("Error sending event to channel", e);
        }
    }
}
