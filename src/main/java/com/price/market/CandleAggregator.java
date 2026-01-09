package com.price.market;

import com.lmax.disruptor.EventHandler;
import com.price.common.CandleProcessor;
import com.price.common.SubscriptionKey;
import com.price.common.config.Instrument;
import com.price.event.buffer.MarketDataEvent;
import com.price.service.SubscriptionProcessor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
public class CandleAggregator implements EventHandler<MarketDataEvent> {
    private final Instrument instrument;
    private final SubscriptionKey subscriptionKey;
    private final List<CandleProcessor> candleProcessors;

    // Current candle state
    private long currentCandleStartTime = -1;
    private double open = 0;
    private double high = Double.MIN_VALUE;
    private double low = Double.MAX_VALUE;
    private double close = 0;
    private long volume = 0;
    private boolean candleStarted = false;

    public CandleAggregator(Instrument instrument, int timeframe, List<? extends CandleProcessor> candleProcessors) {
        this.instrument = instrument;
        this.subscriptionKey = new SubscriptionKey(instrument.fullName(), timeframe);
        this.candleProcessors = new CopyOnWriteArrayList<>(candleProcessors);
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
            long candleStartTime = (eventTime / subscriptionKey.timeframe()) * subscriptionKey.timeframe();
            if (candleStartTime != currentCandleStartTime) {
                flushCandle();
            }
        }
    }

    private void processMarketDataEvent(MarketDataEvent event) {
        // Calculate which candle period this event belongs to
        long eventTime = event.timestamp();
        long candleStartTime = (eventTime / subscriptionKey.timeframe()) * subscriptionKey.timeframe();

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

        this.instrument.candlesEvents().accumulate(1);
        for (CandleProcessor candleProcessor : candleProcessors) {
            candleProcessor.handleCandleEvent(subscriptionKey, currentCandleStartTime,
                    open, high, low, close, volume);
        }

        log.debug("Flushed candle: {}, O={}, H={}, L={}, C={}, V={}",
                subscriptionKey, open, high, low, close, volume);

        reset();
    }

    private void reset() {
        candleStarted = false;
        high = Double.MIN_VALUE;
        low = Double.MAX_VALUE;
        volume = 0;
    }

    public void subscribe(SubscriptionProcessor subscriptionProcessor) {
        candleProcessors.add(subscriptionProcessor);
    }

    public void unsubscribe(SubscriptionProcessor subscriptionProcessor) {
        candleProcessors.remove(subscriptionProcessor);
    }
}
