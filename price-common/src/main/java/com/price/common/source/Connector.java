package com.price.common.source;

public interface Connector extends AutoCloseable {
    void start();
    void register(PriceEventHandler handler);
}
