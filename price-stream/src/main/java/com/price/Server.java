package com.price;

import com.price.common.config.Configuration;
import com.price.common.config.Instrument;
import com.price.market.MarketDataProcessor;
import com.price.market.NonDriftingTimer;
import com.price.market.source.ConnectorFactory;
import com.price.service.HistoryService;
import com.price.service.StreamService;
import com.price.storage.CandlePersistenceProcessor;
import com.price.storage.Repository;
import com.price.storage.db.RepositoryFactory;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

@Slf4j
public class Server {
    private static final CountDownLatch shutdownLatch = new CountDownLatch(1);

    public static void main(String[] args) {
        var configuration = Configuration.read();
        List<AutoCloseable> services = new ArrayList<>();

        NonDriftingTimer timer = new NonDriftingTimer();
        timer.start();
        services.add(timer);

        ConnectorFactory connectorFactory = new ConnectorFactory(configuration.getSources());
        services.add(connectorFactory);

        RepositoryFactory repositoryFactory = new RepositoryFactory(configuration);
        services.add(repositoryFactory);
        List<Repository> repositories = repositoryFactory.getRepositories();

        List<CandlePersistenceProcessor> candleProcessors = new ArrayList<>();
        for (Repository repository : repositories) {
            CandlePersistenceProcessor candleProcessor = new CandlePersistenceProcessor(repository, configuration);
            candleProcessor.start();
            services.add(candleProcessor);
            candleProcessors.add(candleProcessor);
        }

        Map<String, MarketDataProcessor> marketDataProcessorMap = new HashMap<>();
        for (Instrument instrument : configuration.instruments()) {
            MarketDataProcessor mdp = new MarketDataProcessor(instrument, candleProcessors, configuration);
            connectorFactory.getConnector(instrument).register(mdp);
            services.add(mdp);
            timer.add(mdp);
            mdp.start();
            marketDataProcessorMap.put(instrument.fullName(), mdp);
        }

        connectorFactory.start();

        HistoryService historyService = new HistoryService(repositories, configuration);
        historyService.start();
        services.add(historyService);

        StreamService streamService = new StreamService(configuration, marketDataProcessorMap);
        streamService.start();
        services.add(streamService);

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
