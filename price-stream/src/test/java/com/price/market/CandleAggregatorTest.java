import com.price.common.Source;
import com.price.common.config.Instrument;
import com.price.stream.common.SubscriptionKey;
import com.price.stream.event.buffer.MarketDataEvent;
import com.price.stream.market.CandleAggregator;
import com.price.stream.storage.CandlePersistenceProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CandleAggregatorTest {

    @Mock
    private CandlePersistenceProcessor candleProcessor;

    private CandleAggregator candleAggregator;
    private static final Instrument INSTRUMENT = new Instrument("BTCUSDT", Source.BINANCE, new int[]{60000});
    private static final int TIMEFRAME_MS = 60000; // 1 minute
    private static final SubscriptionKey SUBSCRIPTION_KEY = new SubscriptionKey(INSTRUMENT.fullName(), TIMEFRAME_MS);


    @BeforeEach
    void setUp() {
        candleAggregator = new CandleAggregator(INSTRUMENT, TIMEFRAME_MS, List.of(candleProcessor));
    }

    @Test
    void testFirstMarketDataEventStartsNewCandle() throws Exception {
        MarketDataEvent event = createMarketDataEvent(1000, 100.0, 10);

        candleAggregator.onEvent(event, 0, true);

        // No candle should be flushed yet (candle is still open)
        verify(candleProcessor, never()).handleCandleEvent(
                any(SubscriptionKey.class), anyLong(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyLong()
        );
    }

    @Test
    void testMultipleEventsInSameCandlePeriod() throws Exception {
        // All events within first minute (0-59999ms)
        MarketDataEvent event1 = createMarketDataEvent(1000, 100.0, 10);
        MarketDataEvent event2 = createMarketDataEvent(30000, 105.0, 20);
        MarketDataEvent event3 = createMarketDataEvent(50000, 95.0, 15);

        candleAggregator.onEvent(event1, 0, false);
        candleAggregator.onEvent(event2, 1, false);
        candleAggregator.onEvent(event3, 2, true);

        // No candle should be flushed yet (all in same period)
        verify(candleProcessor, never()).handleCandleEvent(
                any(SubscriptionKey.class), anyLong(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyLong()
        );
    }

    @Test
    void testNewCandlePeriodFlushesPreviousCandle() throws Exception {
        // First candle period (0-59999ms)
        MarketDataEvent event1 = createMarketDataEvent(1000, 100.0, 10);
        MarketDataEvent event2 = createMarketDataEvent(30000, 110.0, 20);
        MarketDataEvent event3 = createMarketDataEvent(50000, 95.0, 15);

        // Second candle period (60000-119999ms)
        MarketDataEvent event4 = createMarketDataEvent(60000, 105.0, 25);

        candleAggregator.onEvent(event1, 0, false);
        candleAggregator.onEvent(event2, 1, false);
        candleAggregator.onEvent(event3, 2, false);
        candleAggregator.onEvent(event4, 3, true);

        // Verify first candle was flushed with correct OHLCV values
        ArgumentCaptor<SubscriptionKey> subscriptionKeyCaptor = ArgumentCaptor.forClass(SubscriptionKey.class);
        ArgumentCaptor<Long> timeCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Double> openCaptor = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> highCaptor = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> lowCaptor = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> closeCaptor = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Long> volumeCaptor = ArgumentCaptor.forClass(Long.class);

        verify(candleProcessor, times(1)).handleCandleEvent(
                subscriptionKeyCaptor.capture(),
                timeCaptor.capture(),
                openCaptor.capture(),
                highCaptor.capture(),
                lowCaptor.capture(),
                closeCaptor.capture(),
                volumeCaptor.capture()
        );

        assertEquals(SUBSCRIPTION_KEY, subscriptionKeyCaptor.getValue());
        assertEquals(0L, timeCaptor.getValue()); // First candle starts at 0
        assertEquals(100.0, openCaptor.getValue());
        assertEquals(110.0, highCaptor.getValue());
        assertEquals(95.0, lowCaptor.getValue());
        assertEquals(95.0, closeCaptor.getValue());
        assertEquals(45L, volumeCaptor.getValue()); // 10 + 20 + 15
    }

    @Test
    void testTimerEventFlushesCandle() throws Exception {
        // Market data events in first candle period
        MarketDataEvent dataEvent1 = createMarketDataEvent(1000, 100.0, 10);
        MarketDataEvent dataEvent2 = createMarketDataEvent(30000, 105.0, 20);

        // Timer event in next candle period
        MarketDataEvent timerEvent = createTimerEvent(60000);

        candleAggregator.onEvent(dataEvent1, 0, false);
        candleAggregator.onEvent(dataEvent2, 1, false);
        candleAggregator.onEvent(timerEvent, 2, true);

        // Verify candle was flushed
        verify(candleProcessor, times(1)).handleCandleEvent(
                eq(SUBSCRIPTION_KEY),
                eq(0L),
                eq(100.0), // open
                eq(105.0), // high
                eq(100.0), // low
                eq(105.0), // close
                eq(30L)    // volume
        );
    }

    @Test
    void testTimerEventWithoutCandleDataDoesNothing() throws Exception {
        MarketDataEvent timerEvent = createTimerEvent(60000);

        candleAggregator.onEvent(timerEvent, 0, true);

        // No candle should be flushed (no data events received)
        verify(candleProcessor, never()).handleCandleEvent(
                any(SubscriptionKey.class), anyLong(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyLong()
        );
    }

    @Test
    void testTimerEventInSamePeriodDoesNotFlush() throws Exception {
        // Market data and timer in same candle period
        MarketDataEvent dataEvent = createMarketDataEvent(1000, 100.0, 10);
        MarketDataEvent timerEvent = createTimerEvent(30000);

        candleAggregator.onEvent(dataEvent, 0, false);
        candleAggregator.onEvent(timerEvent, 1, true);

        // No flush should occur (timer in same period)
        verify(candleProcessor, never()).handleCandleEvent(
                any(SubscriptionKey.class), anyLong(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyLong()
        );
    }

    @Test
    void testOHLCVCalculation() throws Exception {
        // Create events with specific prices to test OHLCV calculation
        MarketDataEvent event1 = createMarketDataEvent(1000, 100.0, 5);  // Open
        MarketDataEvent event2 = createMarketDataEvent(10000, 110.0, 10); // High
        MarketDataEvent event3 = createMarketDataEvent(20000, 95.0, 15);  // Low
        MarketDataEvent event4 = createMarketDataEvent(30000, 102.0, 20); // Close
        MarketDataEvent event5 = createMarketDataEvent(40000, 103.0, 25);

        // Event to trigger flush
        MarketDataEvent nextPeriodEvent = createMarketDataEvent(60000, 105.0, 30);

        candleAggregator.onEvent(event1, 0, false);
        candleAggregator.onEvent(event2, 1, false);
        candleAggregator.onEvent(event3, 2, false);
        candleAggregator.onEvent(event4, 3, false);
        candleAggregator.onEvent(event5, 4, false);
        candleAggregator.onEvent(nextPeriodEvent, 5, true);

        verify(candleProcessor).handleCandleEvent(
                eq(SUBSCRIPTION_KEY),
                eq(0L),
                eq(100.0), // open = first price
                eq(110.0), // high = max price
                eq(95.0),  // low = min price
                eq(103.0), // close = last price
                eq(75L)    // volume = sum of all volumes (5+10+15+20+25)
        );
    }

    @Test
    void testMultipleCandlePeriodsInSequence() throws Exception {
        // First candle
        MarketDataEvent candle1Event1 = createMarketDataEvent(1000, 100.0, 10);
        MarketDataEvent candle1Event2 = createMarketDataEvent(30000, 105.0, 20);

        // Second candle
        MarketDataEvent candle2Event1 = createMarketDataEvent(60000, 110.0, 30);
        MarketDataEvent candle2Event2 = createMarketDataEvent(90000, 115.0, 40);

        // Third candle
        MarketDataEvent candle3Event1 = createMarketDataEvent(120000, 120.0, 50);

        candleAggregator.onEvent(candle1Event1, 0, false);
        candleAggregator.onEvent(candle1Event2, 1, false);
        candleAggregator.onEvent(candle2Event1, 2, false);
        candleAggregator.onEvent(candle2Event2, 3, false);
        candleAggregator.onEvent(candle3Event1, 4, true);

        // Verify two candles were flushed
        verify(candleProcessor, times(2)).handleCandleEvent(
                any(SubscriptionKey.class), anyLong(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyLong()
        );
    }

    @Test
    void testDifferentTimeframePeriods() throws Exception {
        // Test with 5-minute timeframe (300000ms)
        Instrument fiveMinInstrument = new Instrument("BTCUSDT", Source.BINANCE, new int[]{300000});
        CandleAggregator fiveMinAggregator = new CandleAggregator(fiveMinInstrument, 300000, List.of(candleProcessor));
        SubscriptionKey fiveMinSubscriptionKey = new SubscriptionKey(fiveMinInstrument.fullName(), 300000);

        MarketDataEvent event1 = createMarketDataEvent(1000, 100.0, 10);
        MarketDataEvent event2 = createMarketDataEvent(150000, 105.0, 20);
        MarketDataEvent event3 = createMarketDataEvent(299000, 95.0, 15);

        // Next period
        MarketDataEvent event4 = createMarketDataEvent(300000, 110.0, 25);

        fiveMinAggregator.onEvent(event1, 0, false);
        fiveMinAggregator.onEvent(event2, 1, false);
        fiveMinAggregator.onEvent(event3, 2, false);
        fiveMinAggregator.onEvent(event4, 3, true);

        verify(candleProcessor).handleCandleEvent(
                eq(fiveMinSubscriptionKey),
                eq(0L),
                eq(100.0),
                eq(105.0),
                eq(95.0),
                eq(95.0),
                eq(45L)
        );
    }

    @Test
    void testCandleStartTimeAlignment() throws Exception {
        // Events at various times within a candle period
        // Should all align to start of period (0ms for first minute)
        MarketDataEvent event1 = createMarketDataEvent(15234, 100.0, 10);
        MarketDataEvent event2 = createMarketDataEvent(45678, 105.0, 20);

        // Next period event
        MarketDataEvent event3 = createMarketDataEvent(75000, 110.0, 30);

        candleAggregator.onEvent(event1, 0, false);
        candleAggregator.onEvent(event2, 1, false);
        candleAggregator.onEvent(event3, 2, true);

        ArgumentCaptor<Long> timeCaptor = ArgumentCaptor.forClass(Long.class);
        verify(candleProcessor).handleCandleEvent(
                any(SubscriptionKey.class), timeCaptor.capture(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyLong()
        );

        // First candle should start at 0 (aligned to timeframe boundary)
        assertEquals(0L, timeCaptor.getValue());
    }

    private MarketDataEvent createMarketDataEvent(long timestamp, double price, long volume) {
        MarketDataEvent event = new MarketDataEvent();
        event.timestamp(timestamp);
        event.price(price);
        event.volume(volume);
        event.type(MarketDataEvent.Type.DATA);
        return event;
    }

    private MarketDataEvent createTimerEvent(long timestamp) {
        MarketDataEvent event = new MarketDataEvent();
        event.timestamp(timestamp);
        event.type(MarketDataEvent.Type.TIMER);
        return event;
    }
}

