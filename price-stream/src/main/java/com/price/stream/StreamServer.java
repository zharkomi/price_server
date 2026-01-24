package com.price.stream;

import com.price.common.config.PriceConfiguration;
import com.price.stream.market.ConnectorFactory;
import com.price.stream.market.MarketDataProcessorFactory;
import com.price.stream.market.NonDriftingTimer;
import com.price.stream.service.StreamService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

@Slf4j
@SpringBootApplication(
        exclude = {DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class},
        scanBasePackages = {"com.price"}
)
public class StreamServer {
    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(StreamServer.class, args);
        context.getBean(NonDriftingTimer.class).start();
        context.getBean(MarketDataProcessorFactory.class).start();
        context.getBean(ConnectorFactory.class).start();
        context.getBean(StreamService.class).start();
    }

    @Bean
    public PriceConfiguration configuration() {
        return PriceConfiguration.read();
    }
}
