package com.price.market;

import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class NonDriftingTimer implements AutoCloseable {
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private volatile boolean running = false;
    private final List<MarketDataProcessor> processors = new CopyOnWriteArrayList<>();

    public void start() {
        running = true;
        scheduleNextEvent();
    }

    private void scheduleNextEvent() {
        if (!running) return;

        // Precalculate next second boundary timestamp
        long currentMillis = System.currentTimeMillis();
        long nextSecond = ((currentMillis / 1000) + 1) * 1000;
        long delay = nextSecond - currentMillis;

        scheduler.schedule(() -> {
            // Send precalculated timestamp (not current time)
            handleEvent(nextSecond);

            // Calculate and schedule next event (prevents drift)
            scheduleNextEvent();
        }, delay, TimeUnit.MILLISECONDS);
    }

    private void handleEvent(long timestamp) {
        log.debug("Timer event at {}", timestamp);
        for (MarketDataProcessor processor : processors) {
            processor.onTimerEvent(timestamp);
        }
    }

    public void stop() {
        running = false;
        scheduler.shutdown();
    }

    @Override
    public void close() throws IOException {

    }

    public void add(MarketDataProcessor mdp) {
        processors.add(mdp);
    }
}