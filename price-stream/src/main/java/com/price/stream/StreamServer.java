package com.price.stream;

import com.price.common.config.PriceConfiguration;
import com.price.stream.market.ConnectorFactory;
import com.price.stream.market.MarketDataAggregateHandler;
import com.price.stream.market.MarketDataHandler;
import com.price.stream.market.NonDriftingTimer;
import com.price.stream.service.ClientSubscriptionAggregateProcessor;
import com.price.stream.service.ClientSubscriptionProcessor;
import com.price.stream.service.StreamService;
import com.price.stream.storage.PersistenceHandler;
import io.netty.channel.socket.SocketChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;

@Slf4j
@SpringBootApplication(
        exclude = {DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class},
        scanBasePackages = {"com.price"}
)
public class StreamServer {
    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(StreamServer.class, args);
        context.getBean(NonDriftingTimer.class).start();
        context.getBean(PersistenceHandler.class).start();
        context.getBean(MarketDataHandler.class).start();
        context.getBean(ConnectorFactory.class).start();
        context.getBean(StreamService.class).start();
    }

    @Bean
    public PriceConfiguration configuration() {
        return PriceConfiguration.read();
    }

    @Bean
    public MarketDataHandler marketDataHandler(PriceConfiguration configuration,
                                               PersistenceHandler persistenceHandler,
                                               ConnectorFactory connectorFactory,
                                               NonDriftingTimer timer) {
        if (configuration.aggregateEvents()) {
            return new MarketDataAggregateHandler(configuration, persistenceHandler, connectorFactory, timer);
        }
        return new MarketDataHandler(configuration, persistenceHandler, connectorFactory, timer);
    }
}
