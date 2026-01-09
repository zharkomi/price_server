package com.price.common;

public interface CandleProcessor {

    void handleCandleEvent(SubscriptionKey subscriptionKey, long time,
                           double open, double high, double low, double close, long volume);
}
