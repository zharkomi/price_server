package com.price.common;

import com.price.market.Instrument;
import com.price.market.source.Source;
import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class Configuration {

    public static final String ENV_BUFFER_SIZE = "ps.buffer.size";
    private static final String ENV_INSTRUMENTS = "ps.instruments";
    private static final String ENV_TIMEFRAME_PREFIX = "ps.timeframe.";
    private static final String ENV_REPOSITORY_TYPE = "ps.repository.type";
    private static final String ENV_CLICKHOUSE_URL = "ps.clickhouse.url";
    private static final String ENV_CLICKHOUSE_USER = "ps.clickhouse.user";
    private static final String ENV_CLICKHOUSE_PASSWORD = "ps.clickhouse.password";
    private static final String INSTRUMENT_DELIMITER = ",";
    private static final String INSTRUMENT_SEPARATOR = "@";
    public static final String DEFAULT_BUFFER_SIZE = "4096";
    public static final String DEFAULT_REPOSITORY_TYPE = "CLICKHOUSE";

    public final List<Instrument> instruments;
    public final List<Source> sources;
    public final String repositoryType;
    public final String clickhouseUrl;
    public final String clickhouseUser;
    public final String clickhousePassword;

    public Configuration() {
        this.instruments = parseInstruments();
        this.sources = instruments.stream().map(Instrument::source).distinct().toList();
        this.repositoryType = System.getenv().getOrDefault(ENV_REPOSITORY_TYPE, DEFAULT_REPOSITORY_TYPE).toUpperCase();
        this.clickhouseUrl = System.getenv(ENV_CLICKHOUSE_URL);
        this.clickhouseUser = System.getenv(ENV_CLICKHOUSE_USER);
        this.clickhousePassword = System.getenv(ENV_CLICKHOUSE_PASSWORD);
    }

    private List<Instrument> parseInstruments() {
        String instrumentsProperty = System.getenv(ENV_INSTRUMENTS);
        if (instrumentsProperty == null || instrumentsProperty.trim().isEmpty()) {
            throw new IllegalStateException(ENV_INSTRUMENTS + " environment variable is not set");
        }

        List<Instrument> result = new ArrayList<>();
        String[] instrumentSpecs = instrumentsProperty.split(INSTRUMENT_DELIMITER);

        for (String spec : instrumentSpecs) {
            Instrument instrument = parseInstrument(spec);
            if (instrument == null) continue;
            result.add(instrument);
        }

        return result;
    }

    @Nullable
    private Instrument parseInstrument(String spec) {
        spec = spec.trim();
        if (spec.isEmpty()) {
            return null;
        }

        // Parse format: SYMBOL@EXCHANGE
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
        int[] timeframes = parseTimeframes(spec);

        Instrument instrument = new Instrument(name, source, timeframes);
        return instrument;
    }

    private int[] parseTimeframes(String spec) {
        String propertyKey = ENV_TIMEFRAME_PREFIX + spec;
        String timeframesProperty = System.getenv(propertyKey);

        if (timeframesProperty == null || timeframesProperty.trim().isEmpty()) {
            throw new IllegalStateException("Timeframes not configured for " + spec +
                    ". Expected environment variable: " + propertyKey);
        }

        String[] timeframeSpecs = timeframesProperty.split(INSTRUMENT_DELIMITER);
        if (timeframeSpecs.length == 0) {
            throw new IllegalArgumentException("No timeframes specified for instrument " + spec);
        }

        int[] result = new int[timeframeSpecs.length];

        for (int i = 0; i < timeframeSpecs.length; i++) {
            String timeframeSpec = timeframeSpecs[i].trim();
            if (timeframeSpec.isEmpty()) {
                throw new IllegalArgumentException("Empty timeframe specification for instrument " + spec);
            }

            int timeframe = Util.parseTimeframeToMilliseconds(timeframeSpec);
            if (timeframe <= 0) {
                throw new IllegalArgumentException("Invalid timeframe: " + timeframeSpec +
                        " for instrument " + spec + ". Timeframe must be positive.");
            }
            result[i] = timeframe;
        }

        return result;
    }

    public int getDisruptorBufferSize() {
        return NumberUtils.toInt(System.getenv().getOrDefault(ENV_BUFFER_SIZE, DEFAULT_BUFFER_SIZE));
    }
}
