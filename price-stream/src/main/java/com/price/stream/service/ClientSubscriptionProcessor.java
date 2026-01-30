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

    protected final RingBuffer<CandleEvent> ringBuffer;
    protected static final ObjectMapper MAPPER = new ObjectMapper();
    protected final SocketChannel channel;

    private final MarketDataHandler marketDataHandler;
    private final Set<SubscriptionKey> subscriptions = ConcurrentHashMap.newKeySet();
    private final Disruptor<CandleEvent> disruptor;

    public ClientSubscriptionProcessor(SocketChannel channel, MarketDataHandler marketDataHandler, int clientBufferSize) {
        this.channel = channel;
        this.marketDataHandler = marketDataHandler;

        this.disruptor = new Disruptor<>(
                CandleEvent::new,
                clientBufferSize,
                Executors.defaultThreadFactory(),
                ProducerType.MULTI,
                new YieldingWaitStrategy()
        );

        disruptor.handleEventsWith(this::sendEvent);
        this.ringBuffer = disruptor.getRingBuffer();
    }

    public synchronized void start() {
        disruptor.start();
    }

    public synchronized void stop() {
        for (SubscriptionKey key : new ArrayList<>(subscriptions)) {
            unsubscribe(key.instrument(), key.timeframe());
        }
        disruptor.halt();
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

    @Override
    public void handleCandleEvent(SubscriptionKey subscriptionKey, long time, double open, double high, double low, double close, long volume) {
        log.debug("Processing candle event for {}", subscriptionKey);
        long sequence = ringBuffer.next();
        try {
            CandleEvent event = ringBuffer.get(sequence);
            event.endOfBatch(false);
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

    protected void sendEvent(CandleEvent event, long sequence, boolean endOfBatch) {
        try {
            String json = MAPPER.writeValueAsString(event);
            channel.writeAndFlush(new TextWebSocketFrame(json));
        } catch (Exception e) {
            log.error("Error sending event to channel", e);
        }
    }
}
