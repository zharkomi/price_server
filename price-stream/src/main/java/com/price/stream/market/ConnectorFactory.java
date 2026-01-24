package com.price.stream.market;

import com.price.common.config.Instrument;
import com.price.common.config.PriceConfiguration;
import com.price.common.source.Connector;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ConnectorFactory implements AutoCloseable {
    public static final String BASE_PACKAGE = "com.price.source.";
    public static final String CONNECTOR_CLASS = ".Connector";

    private final Map<String, Connector> connectors;

    public ConnectorFactory(PriceConfiguration configuration) {
        this.connectors = configuration.getSources().stream()
                .collect(Collectors.toMap(source -> source, this::createConnector));
    }

    private Connector createConnector(String source) {
        String className = BASE_PACKAGE + source.toLowerCase() + CONNECTOR_CLASS;
        try {
            Class<?> clazz = Class.forName(className);
            return (Connector) clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create connector for source: " + source, e);
        }
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
