package com.price.event;

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
public class CandleEvent extends TraceableEvent {
    private String instrument;
    private int timeframeMs;
    private long time;
    private float open;
    private float high;
    private float low;
    private float close;
    private float volume;
}
