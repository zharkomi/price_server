package com.price.common.source;

import com.price.common.config.Instrument;

public interface PriceEventHandler {
    Instrument getInstrument();
    void handlePriceEvent(long timestamp, double price, long volume);
}
