package com.price.common.db;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.price.common.TraceableEvent;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

@Getter
@Setter
@NoArgsConstructor
@ToString
@Accessors(fluent = true)
@JsonIgnoreProperties({"startTimeNano"})
public class CandleEvent extends TraceableEvent {
    @JsonProperty("i")
    private String instrument;
    @JsonProperty("f")
    private int timeframeMs;
    @JsonProperty("t")
    private long time;
    @JsonProperty("o")
    private double open;
    @JsonProperty("h")
    private double high;
    @JsonProperty("l")
    private double low;
    @JsonProperty("c")
    private double close;
    @JsonProperty("v")
    private long volume;

    @JsonIgnore
    private boolean endOfBatch;
}
