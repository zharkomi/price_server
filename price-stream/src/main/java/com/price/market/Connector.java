package com.price.market;

public interface Connector extends AutoCloseable {
    void start();

    void register(MarketDataProcessor mdp);
}
