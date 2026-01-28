package com.price.stream.market;

import com.price.common.config.Instrument;
import com.price.common.config.PriceConfiguration;
import com.price.stream.service.ClientSubscriptionProcessor;
import com.price.stream.storage.PersistenceHandler;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Phaser;

@Service
public class MarketDataHandler {

    private final Map<String, InstrumentDataProcessor> marketDataProcessorMap;
    private final List<ClientSubscriptionProcessor> clients = new CopyOnWriteArrayList<>();
    private final Phaser phaser;

    public MarketDataHandler(PriceConfiguration configuration,
                             PersistenceHandler persistenceHandler,
                             ConnectorFactory connectorFactory,
                             NonDriftingTimer timer) {
        this.marketDataProcessorMap = new HashMap<>();
        this.phaser = new Phaser(configuration.instruments().size()) {
            @Override
            protected boolean onAdvance(int phase, int registeredParties) {
                MarketDataHandler.this.clients.forEach(ClientSubscriptionProcessor::timeFrameProcessed);
                return false;
            }
        };
        for (Instrument instrument : configuration.instruments()) {
            InstrumentDataProcessor mdp = new InstrumentDataProcessor(this, instrument, persistenceHandler.getCandleProcessors(), configuration);
            connectorFactory.getConnector(instrument).register(mdp);
            timer.add(mdp);
            marketDataProcessorMap.put(instrument.fullName(), mdp);
        }
    }

    public void start() {
        marketDataProcessorMap.values().forEach(InstrumentDataProcessor::start);
    }

    public void close() throws Exception {
        for (InstrumentDataProcessor processor : marketDataProcessorMap.values()) {
            processor.close();
        }
    }

    public InstrumentDataProcessor get(String instrument) {
        return marketDataProcessorMap.get(instrument);
    }

    public void instrumentProcessed() {
        phaser.arrive();
    }

    public void register(ClientSubscriptionProcessor clientSubscriptionProcessor) {
        this.clients.add(clientSubscriptionProcessor);
    }

    public void deregister(ClientSubscriptionProcessor clientSubscriptionProcessor) {
        this.clients.remove(clientSubscriptionProcessor);
    }
}
