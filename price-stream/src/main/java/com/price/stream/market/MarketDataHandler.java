package com.price.stream.market;

import com.lmax.disruptor.dsl.EventHandlerGroup;
import com.price.common.config.Instrument;
import com.price.common.config.PriceConfiguration;
import com.price.stream.event.buffer.MarketDataEvent;
import com.price.stream.service.ClientSubscriptionProcessor;
import com.price.stream.storage.PersistenceHandler;
import io.netty.channel.socket.SocketChannel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class MarketDataHandler {

    private final Map<String, InstrumentDataProcessor> marketDataProcessorMap;
    protected final int clientBufferSize;

    public MarketDataHandler(PriceConfiguration configuration,
                             PersistenceHandler persistenceHandler,
                             ConnectorFactory connectorFactory,
                             NonDriftingTimer timer) {
        this.marketDataProcessorMap = new HashMap<>();
        this.clientBufferSize = configuration.clientBufferSize();
        for (Instrument instrument : configuration.instruments()) {
            InstrumentDataProcessor mdp = new InstrumentDataProcessor(this, instrument, persistenceHandler.getCandleProcessors(), configuration);
            connectorFactory.getConnector(instrument).register(mdp);
            timer.add(mdp);
            marketDataProcessorMap.put(instrument.fullName(), mdp);
        }
    }

    public ClientSubscriptionProcessor createProcessor(SocketChannel channel) {
        return new ClientSubscriptionProcessor(channel, this, clientBufferSize);
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

    public void appendHandlers(EventHandlerGroup<MarketDataEvent> group) {

    }
}
