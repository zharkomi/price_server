package com.price.market;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.Instant;
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
        Instant now = Instant.now();

        // Precalculate next second boundary timestamp in millis
        long nextSecond = ((now.toEpochMilli() / 1000) + 1) * 1000;

        // Calculate delay in nanos for precise scheduling
        long delayNanos = 1_000_000_000L - now.getNano();

        scheduler.schedule(() -> {
            handleEvent(nextSecond);
            scheduleNextEvent();
        }, delayNanos, TimeUnit.NANOSECONDS);
    }

    private void handleEvent(long timestamp) {
        log.debug("Timer event at {}", timestamp);
        for (MarketDataProcessor processor : processors) {
            processor.handleTimerEvent(timestamp);
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