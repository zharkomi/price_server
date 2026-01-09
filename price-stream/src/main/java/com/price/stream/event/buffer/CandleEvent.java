package com.price.stream.event.buffer;

import com.price.stream.common.TraceableEvent;
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
public class CandleEvent extends TraceableEvent {
    private String instrument;
    private int timeframeMs;
    private long time;
    private double open;
    private double high;
    private double low;
    private double close;
    private long volume;
}
