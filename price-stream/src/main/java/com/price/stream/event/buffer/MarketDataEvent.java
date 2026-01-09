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
public class MarketDataEvent extends TraceableEvent {
    private long timestamp;
    private double price;
    private long volume;
    private Type type;

    public enum Type {
        DATA,
        TIMER
    }
}
