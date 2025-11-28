package com.price.market.source;

import com.price.market.Connector;

public enum Source {
    BINANCE {
        @Override
        public Connector createConnector() {
            return new BinanceConnector();
        }
    };

    public abstract Connector createConnector();
}
