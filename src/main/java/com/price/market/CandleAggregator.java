package com.price.market;

import com.lmax.disruptor.EventHandler;
import com.price.storage.CandleEvent;
import com.price.storage.CandleProcessor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CandleAggregator implements EventHandler<MarketDataEvent> {
    private final long timeframeMillis;
    private final CandleProcessor candleProcessor;

    // Current candle state
    private long currentCandleStartTime = -1;
    private float open = 0;
    private float high = Float.MIN_VALUE;
    private float low = Float.MAX_VALUE;
    private float close = 0;
    private float volume = 0;
    private boolean candleStarted = false;

    public CandleAggregator(long timeframeMillis, CandleProcessor candleProcessor) {
        this.timeframeMillis = timeframeMillis;
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
            long candleStartTime = (eventTime / timeframeMillis) * timeframeMillis;
            if (candleStartTime != currentCandleStartTime) {
                flushCandle();
            }
        }
    }

    private void processMarketDataEvent(MarketDataEvent event) {
        // Calculate which candle period this event belongs to
        long eventTime = event.timestamp();
        long candleStartTime = (eventTime / timeframeMillis) * timeframeMillis;

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

        CandleEvent candleEvent = new CandleEvent(currentCandleStartTime, open, high, low, close, volume);
        candleProcessor.handleEvent(candleEvent);

        log.debug("Flushed candle: timeframeMillis={}, O={}, H={}, L={}, C={}, V={}",
                timeframeMillis, open, high, low, close, volume);

        reset();
    }

    private void reset() {
        candleStarted = false;
        high = Float.MIN_VALUE;
        low = Float.MAX_VALUE;
        volume = 0;
    }
}
