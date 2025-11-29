package com.price.market.source;

import com.price.market.Connector;
import com.price.market.Instrument;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ConnectorFactory implements AutoCloseable {
    private final Map<Source, Connector> connectors;

    public ConnectorFactory(List<Source> sources) {
        this.connectors = sources.stream()
                .collect(Collectors.toMap(source -> source, Source::createConnector));
    }

    public void start() {
        connectors.values().forEach(Connector::start);
    }

    public Connector getConnector(Instrument instrument) {
        return connectors.get(instrument.source());
    }

    @Override
    public void close() throws Exception {
        for (Connector connector : connectors.values()) {
            connector.close();
        }
    }
}
