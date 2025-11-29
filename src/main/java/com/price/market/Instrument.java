package com.price.market;

import com.price.market.source.Source;

public record Instrument(String name, Source source, int[] timeframes) {
    public String fullName() {
        return name + "@" + source.name();
    }
}
