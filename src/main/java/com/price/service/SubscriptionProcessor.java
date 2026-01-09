package com.price.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.price.common.CandleProcessor;
import com.price.common.SubscriptionKey;
import com.price.event.client.InstrumentEvent;
import com.price.market.MarketDataProcessor;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Phaser;
import java.util.function.Consumer;

@Slf4j
public class SubscriptionProcessor implements CandleProcessor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Phaser phaser = new Phaser(1);

    private final SocketChannel channel;
    private final Map<String, MarketDataProcessor> marketDataProcessorMap;
    private final Map<SubscriptionKey, InstrumentEvent> instrumentEvents = new ConcurrentHashMap<>();
    private final Thread senderThread;
    private final Set<SubscriptionKey> subscriptions = new HashSet<>();

    public SubscriptionProcessor(SocketChannel channel, Map<String, MarketDataProcessor> marketDataProcessorMap) {
        this.channel = channel;
        this.marketDataProcessorMap = marketDataProcessorMap;
        this.senderThread = Thread.ofVirtual().unstarted(this::handleEvents);
    }

    public void start() {
        senderThread.start();
    }

    public synchronized void subscribe(String instrument, int timeframe) {
        subscriptions.add(new SubscriptionKey(instrument, timeframe));
        phaser.register();
        handle(instrument, p -> p.subscribe(timeframe, this));
        this.notifyAll();
    }

    public synchronized void unsubscribe(String instrument, int timeframe) {
        boolean removed = subscriptions.remove(new SubscriptionKey(instrument, timeframe));
        if (removed) {
            handle(instrument, p -> p.unsubscribe(timeframe, this));
            if (phaser.getRegisteredParties() > 1) {
                try {
                    phaser.arriveAndDeregister();
                } catch (Exception e) {
                    log.error("Subscription state exception", e);
                }
            }
        }
    }

    private synchronized boolean waitForSubscriptions() {
        try {
            this.wait(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        return true;
    }

    public synchronized void stop() {
        senderThread.interrupt();
        phaser.forceTermination();
        subscriptions.forEach(key ->
                handle(key.instrument(), p -> p.unsubscribe(key.timeframe(), this))
        );
    }

    private void handle(String instrument, Consumer<MarketDataProcessor> command) {
        MarketDataProcessor processor = marketDataProcessorMap.get(instrument);
        if (processor != null) {
            command.accept(processor);
        } else {
            log.error("MarketDataProcessor not found for instrument: {}", instrument);
        }
    }

    private void handleEvents() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                phaser.arriveAndAwaitAdvance();
            } catch (Exception e) {
                log.error("Subscription state exception", e);
            }
            if (phaser.getRegisteredParties() == 1) {
                if (!waitForSubscriptions()) {
                    break;
                }
            }
            List<InstrumentEvent> events = new ArrayList<>(instrumentEvents.values());
            instrumentEvents.clear();
            try {
                if (!events.isEmpty()) {
                    String json = MAPPER.writeValueAsString(events);
                    channel.writeAndFlush(new TextWebSocketFrame(json));
                }
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize events", e);
            }
        }
    }

    @Override
    public void handleCandleEvent(SubscriptionKey subscriptionKey, long time, double open, double high, double low, double close, long volume) {
        log.info("Processing candle event for {}", subscriptionKey);
        InstrumentEvent event = new InstrumentEvent(subscriptionKey.instrument(), time, subscriptionKey.timeframe(), open, high, low, close, volume);
        instrumentEvents.put(subscriptionKey, event);
    }

    public void instrumentProcessed() {
        try {
            phaser.arrive();
        } catch (Exception e) {
            log.error("Subscription state exception", e);
        }
    }
}
