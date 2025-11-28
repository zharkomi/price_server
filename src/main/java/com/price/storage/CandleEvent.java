package com.price.storage;

import com.price.common.TraceableEvent;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = false)
public class CandleEvent extends TraceableEvent {
    long time;
    float open;
    float high;
    float low;
    float close;
    float volume;
}
