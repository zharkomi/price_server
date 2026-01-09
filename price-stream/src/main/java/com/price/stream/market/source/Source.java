package com.price.stream.market.source;

import com.price.stream.market.Connector;

public enum Source {
    BINANCE {
        @Override
        public Connector createConnector() {
            return new BinanceConnector();
        }
    };

    public abstract Connector createConnector();
}
