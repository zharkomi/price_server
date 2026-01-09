package com.price.common.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.price.common.Source;
import com.price.common.Util;

import java.util.List;
import java.util.concurrent.atomic.LongAccumulator;

public record Instrument(String name,
                         Source source,
                         int[] timeframes,
                         @JsonIgnore LongAccumulator marketEvents,
                         @JsonIgnore LongAccumulator candlesEvents) {

    public Instrument(String name, Source source, int[] timeframes) {
        this(name, source, timeframes,
                new LongAccumulator(Long::sum, 0),
                new LongAccumulator(Long::sum, 0));
    }

    @JsonCreator
    public static Instrument fromJson(
            @JsonProperty("name") String name,
            @JsonProperty("source") Source source,
            @JsonProperty("timeframes") List<String> timeframes) {
        int[] tf = timeframes.stream()
                .mapToInt(Util::parseTimeframeToMilliseconds)
                .toArray();
        return new Instrument(name, source, tf);
    }

    public String fullName() {
        return name + "@" + source.name();
    }
}
