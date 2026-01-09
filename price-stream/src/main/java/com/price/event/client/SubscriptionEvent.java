package com.price.event.client;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class SubscriptionEvent {

    public enum Type {
        SUBSCRIBE,
        UNSUBSCRIBE
    }

    private final Type type;
    private final String instrument;
    private final int timeframe;

    @JsonCreator
    public SubscriptionEvent(
            @JsonProperty("type") Type type,
            @JsonProperty("instrument") String instrument,
            @JsonProperty("timeframe") int timeframe) {
        this.type = type;
        this.instrument = instrument;
        this.timeframe = timeframe;
    }
}
