package com.price.stream.market;

public interface Connector extends AutoCloseable {
    void start();

    void register(MarketDataProcessor mdp);
}
