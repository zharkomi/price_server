package com.price.market;

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
public class MarketDataEvent extends TraceableEvent {
    private long timestamp;
    private float price;
    private float volume;
    private Type type;

    public enum Type {
        DATA,
        TIMER
    }

    public MarketDataEvent(long timestamp, float price, float volume) {
        this.timestamp = timestamp;
        this.price = price;
        this.volume = volume;
    }

    public void copyFrom(MarketDataEvent marketData) {
        this.timestamp = marketData.timestamp;
        this.price = marketData.price;
        this.volume = marketData.volume;
    }
}
