package com.price.stream.market;

import com.price.common.config.Instrument;
import com.price.common.config.PriceConfiguration;
import com.price.stream.storage.PersistenceProcessorFactory;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class MarketDataProcessorFactory {

    public final Map<String, MarketDataProcessor> marketDataProcessorMap;

    public MarketDataProcessorFactory(PriceConfiguration configuration,
                                      PersistenceProcessorFactory persistenceProcessorFactory,
                                      ConnectorFactory connectorFactory,
                                      NonDriftingTimer timer) {
        marketDataProcessorMap = new HashMap<>();
        for (Instrument instrument : configuration.instruments()) {
            MarketDataProcessor mdp = new MarketDataProcessor(instrument, persistenceProcessorFactory.getCandleProcessors(), configuration);
            connectorFactory.getConnector(instrument).register(mdp);
            timer.add(mdp);
            marketDataProcessorMap.put(instrument.fullName(), mdp);
        }
    }

    public void start() {
        marketDataProcessorMap.values().forEach(MarketDataProcessor::start);
    }

    public void close() throws Exception {
        for (MarketDataProcessor processor : marketDataProcessorMap.values()) {
            processor.close();
        }
    }
}
