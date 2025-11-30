package com.price.service.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class HistoryRequest {
    private final String symbol;
    private final String interval;
    private final long fromTimestamp;
    private final long toTimestamp;
    private final int timeframeMs;

    public static HistoryRequest of(String symbol, String interval, long fromTimestamp, long toTimestamp, int timeframeMs) {
        return new HistoryRequest(symbol, interval, fromTimestamp, toTimestamp, timeframeMs);
    }
}
