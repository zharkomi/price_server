package com.price.market;

import com.lmax.disruptor.EventHandler;
import com.price.event.MarketDataEvent;
import com.price.storage.CandleProcessor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CandleAggregator implements EventHandler<MarketDataEvent> {
    private final String instrument;
    private final int timeframeMs;
    private final CandleProcessor candleProcessor;

    // Current candle state
    private long currentCandleStartTime = -1;
    private double open = 0;
    private double high = Double.MIN_VALUE;
    private double low = Double.MAX_VALUE;
    private double close = 0;
    private long volume = 0;
    private boolean candleStarted = false;

    public CandleAggregator(String instrument, int timeframeMs, CandleProcessor candleProcessor) {
        this.instrument = instrument;
        this.timeframeMs = timeframeMs;
        this.candleProcessor = candleProcessor;
    }

    @Override
    public void onEvent(MarketDataEvent event, long sequence, boolean endOfBatch) throws Exception {
        if (event.type() == MarketDataEvent.Type.TIMER) {
            processTimerEvent(event);
            return;
        }
        processMarketDataEvent(event);
    }

    private void processTimerEvent(MarketDataEvent event) {
        if (candleStarted) {
            long eventTime = event.timestamp();
            long candleStartTime = (eventTime / timeframeMs) * timeframeMs;
            if (candleStartTime != currentCandleStartTime) {
                flushCandle();
            }
        }
    }

    private void processMarketDataEvent(MarketDataEvent event) {
        // Calculate which candle period this event belongs to
        long eventTime = event.timestamp();
        long candleStartTime = (eventTime / timeframeMs) * timeframeMs;

        // If this is a new candle period, flush the previous one
        if (candleStarted && candleStartTime != currentCandleStartTime) {
            flushCandle();
        }

        if (!candleStarted || candleStartTime != currentCandleStartTime) {
            // Start a new candle
            currentCandleStartTime = candleStartTime;
            open = event.price();
            high = event.price();
            low = event.price();
            close = event.price();
            volume = event.volume();
            candleStarted = true;
        } else {
            // Update existing candle
            high = Math.max(high, event.price());
            low = Math.min(low, event.price());
            close = event.price();
            volume += event.volume();
        }
    }

    private void flushCandle() {
        if (!candleStarted) {
            return;
        }

        candleProcessor.handleCandleEvent(instrument, timeframeMs, currentCandleStartTime,
                open, high, low, close, volume);

        log.debug("Flushed candle: instrument={}, timeframeMs={}, O={}, H={}, L={}, C={}, V={}",
                instrument, timeframeMs, open, high, low, close, volume);

        reset();
    }

    private void reset() {
        candleStarted = false;
        high = Double.MIN_VALUE;
        low = Double.MAX_VALUE;
        volume = 0;
    }
}
