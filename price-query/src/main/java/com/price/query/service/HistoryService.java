package com.price.query.service;

import com.price.common.storage.Candle;
import com.price.common.storage.QueryRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class HistoryService {

    private final QueryRepository repository;

    public HistoryService(QueryRepository repository) {
        this.repository = repository;
    }

    public List<Candle> getCandles(String instrument, String interval, long from, long to) throws Exception {
        int timeframeMs = parseTimeframeToMilliseconds(interval);
        return repository.queryCandles(instrument, timeframeMs, from, to);
    }

    private int parseTimeframeToMilliseconds(String timeframe) {
        if (timeframe == null || timeframe.isEmpty()) {
            throw new IllegalArgumentException("Timeframe cannot be null or empty");
        }

        char unit = timeframe.charAt(timeframe.length() - 1);
        String valueStr = timeframe.substring(0, timeframe.length() - 1);
        int value = Integer.parseInt(valueStr);

        return switch (unit) {
            case 's' -> value * 1000;
            case 'm' -> value * 60 * 1000;
            case 'h' -> value * 3600 * 1000;
            case 'd' -> value * 24 * 3600 * 1000;
            default -> throw new IllegalArgumentException("Invalid timeframe unit: " + unit);
        };
    }
}
