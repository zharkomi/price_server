package com.price.common;

import com.price.market.Instrument;
import com.price.market.source.Source;
import org.apache.commons.lang3.math.NumberUtils;

import java.util.ArrayList;
import java.util.List;

public class Configuration {

    public static final String ENV_BUFFER_SIZE = "ps.buffer.size";
    private static final String ENV_INSTRUMENTS = "ps.instruments";
    private static final String ENV_TIMEFRAME_PREFIX = "ps.timeframe.";
    private static final String INSTRUMENT_DELIMITER = ",";
    private static final String INSTRUMENT_SEPARATOR = "@";
    public static final int DEFAULT_BUFFER_SIZE = 4096;

    public final List<Instrument> instruments;
    public final List<Source> sources;

    public Configuration() {
        this.instruments = parseInstruments();
        this.sources = instruments.stream().map(Instrument::source).distinct().toList();
    }

    private List<Instrument> parseInstruments() {
        String instrumentsProperty = System.getenv(ENV_INSTRUMENTS);
        if (instrumentsProperty == null || instrumentsProperty.trim().isEmpty()) {
            throw new IllegalStateException(ENV_INSTRUMENTS + " environment variable is not set");
        }

        List<Instrument> result = new ArrayList<>();
        String[] instrumentSpecs = instrumentsProperty.split(INSTRUMENT_DELIMITER);

        for (String spec : instrumentSpecs) {
            spec = spec.trim();
            if (spec.isEmpty()) {
                continue;
            }

            // Parse format: BTCUSD@BINANCE
            String[] parts = spec.split(INSTRUMENT_SEPARATOR);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid instrument format: " + spec + ". Expected format: NAME" + INSTRUMENT_SEPARATOR + "SOURCE");
            }

            String name = parts[0].trim();
            String sourceStr = parts[1].trim();

            Source source;
            try {
                source = Source.valueOf(sourceStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unknown source: " + sourceStr);
            }

            // Parse timeframes for this instrument
            int[] timeframes = parseTimeframes(name, source);

            result.add(new Instrument(name, source, timeframes));
        }

        return result;
    }

    private int[] parseTimeframes(String name, Source source) {
        String propertyKey = ENV_TIMEFRAME_PREFIX + name + INSTRUMENT_SEPARATOR + source;
        String timeframesProperty = System.getenv(propertyKey);

        if (timeframesProperty == null || timeframesProperty.trim().isEmpty()) {
            throw new IllegalStateException("Timeframes not configured for " + name + INSTRUMENT_SEPARATOR + source +
                    ". Expected environment variable: " + propertyKey);
        }

        String[] timeframeSpecs = timeframesProperty.split(INSTRUMENT_DELIMITER);
        int[] result = new int[timeframeSpecs.length];

        for (int i = 0; i < timeframeSpecs.length; i++) {
            result[i] = parseTimeframeToSeconds(timeframeSpecs[i].trim());
        }

        return result;
    }

    private int parseTimeframeToSeconds(String timeframe) {
        if (timeframe.isEmpty()) {
            throw new IllegalArgumentException("Empty timeframe value");
        }

        // Extract number and unit (e.g., "1m" -> number=1, unit="m")
        int numberEnd = 0;
        while (numberEnd < timeframe.length() && Character.isDigit(timeframe.charAt(numberEnd))) {
            numberEnd++;
        }

        if (numberEnd == 0) {
            throw new IllegalArgumentException("Invalid timeframe format: " + timeframe);
        }

        int value = Integer.parseInt(timeframe.substring(0, numberEnd));
        String unit = timeframe.substring(numberEnd).toLowerCase();

        return switch (unit) {
            case "s" -> value;
            case "m" -> value * 60;
            case "h" -> value * 3600;
            case "d" -> value * 86400;
            default -> throw new IllegalArgumentException("Unknown timeframe unit: " + unit +
                    ". Supported units: s, m, h, d");
        };
    }

    public int getDisruptorBufferSize() {
        String bufferSizeProperty = System.getenv(ENV_BUFFER_SIZE);
        int bufferSize = NumberUtils.toInt(bufferSizeProperty, DEFAULT_BUFFER_SIZE);
        return bufferSize > 0 ? bufferSize : DEFAULT_BUFFER_SIZE;
    }
}
