package com.price.query.storage;

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
