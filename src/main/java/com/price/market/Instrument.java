package com.price.market;

import com.price.market.source.Source;

import java.util.concurrent.atomic.LongAccumulator;

public record Instrument(String name,
                         Source source,
                         int[] timeframes,
                         LongAccumulator marketEvents,
                         LongAccumulator candlesEvents) {
    public Instrument(String name, Source source, int[] timeframes) {
        this(name, source, timeframes,
                new LongAccumulator(Long::sum, 0),
                new LongAccumulator(Long::sum, 0));
    }

    public String fullName() {
        return name + "@" + source.name();
    }
}
