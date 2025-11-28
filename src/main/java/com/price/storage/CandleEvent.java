package com.price.storage;

public record CandleEvent(int timeframe, float open, float high, float low, float close, float volume) {
}
