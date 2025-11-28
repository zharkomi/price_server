package com.price;

import com.price.common.Configuration;
import com.price.market.Instrument;
import com.price.market.MarketDataProcessor;
import com.price.market.NonDriftingTimer;
import com.price.market.source.ConnectorFactory;
import com.price.storage.CandleProcessor;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

@Slf4j
public class Server {
    private static final CountDownLatch shutdownLatch = new CountDownLatch(1);

    public static void main(String[] args) {
        var configuration = new Configuration();
        List<AutoCloseable> services = new ArrayList<>();

        NonDriftingTimer timer = new NonDriftingTimer();
        timer.start();
        services.add(timer);

        ConnectorFactory connectorFactory = new ConnectorFactory(configuration.sources);
        services.add(connectorFactory);

        CandleProcessor candleProcessor = new CandleProcessor();
        services.add(candleProcessor);

        for (Instrument instrument : configuration.instruments) {
            MarketDataProcessor mdp = new MarketDataProcessor(instrument, candleProcessor, configuration);
            connectorFactory.getConnector(instrument).register(mdp);
            services.add(mdp);
            timer.add(mdp);
            mdp.start();
        }

        connectorFactory.start();

        addShutdownHook(services);
        log.info("Server started successfully.");

        try {
            shutdownLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Server interrupted");
        }
    }

    private static void addShutdownHook(List<AutoCloseable> services) {
        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down server...");

            // Close all services
            if (services != null) {
                for (AutoCloseable service : services) {
                    try {
                        service.close();
                    } catch (Exception e) {
                        System.err.println("Error closing service: " + e.getMessage());
                    }
                }
            }

            log.info("Server stopped");
            shutdownLatch.countDown();
        }, "shutdown-hook"));
    }
}
