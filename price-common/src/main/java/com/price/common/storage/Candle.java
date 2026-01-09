package com.price.common.storage;

public record Candle(
    String instrument,
    int timeframeMs,
    long time,
    double open,
    double high,
    double low,
    double close,
    double volume
) {}
