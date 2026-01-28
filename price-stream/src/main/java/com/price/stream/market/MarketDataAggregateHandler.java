package com.price.stream.market;

import com.lmax.disruptor.dsl.EventHandlerGroup;
import com.price.common.config.PriceConfiguration;
import com.price.stream.event.buffer.MarketDataEvent;
import com.price.stream.service.ClientSubscriptionAggregateProcessor;
import com.price.stream.storage.PersistenceHandler;
import io.netty.channel.socket.SocketChannel;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Phaser;

@Slf4j
public class MarketDataAggregateHandler extends MarketDataHandler<ClientSubscriptionAggregateProcessor> {

    private final Phaser phaser;

    public MarketDataAggregateHandler(PriceConfiguration configuration, PersistenceHandler persistenceHandler, ConnectorFactory connectorFactory, NonDriftingTimer timer) {
        super(configuration, persistenceHandler, connectorFactory, timer);
        this.phaser = new Phaser(configuration.instruments().size()) {
            @Override
            protected boolean onAdvance(int phase, int registeredParties) {
                log.debug("All instruments processed for timeframe {}", phase);
                MarketDataAggregateHandler.this.clients.forEach(ClientSubscriptionAggregateProcessor::timeFrameProcessed);
                return false;
            }
        };
    }

    @Override
    public ClientSubscriptionAggregateProcessor createProcessor(SocketChannel channel){
        return new ClientSubscriptionAggregateProcessor(channel, this);
    }

    public void appendHandlers(EventHandlerGroup<MarketDataEvent> group) {
        group.handleEventsWith(new ClientNotifier(this));
    }

    public void instrumentProcessed() {
        log.debug("Instrument processed");
        phaser.arrive();
    }
}
