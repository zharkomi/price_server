package com.price.stream.storage;

import com.price.common.config.PriceConfiguration;
import com.price.common.db.RepositoryContainer;
import com.price.common.db.SaveRepository;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class PersistenceProcessorFactory {

    @Getter
    private final List<CandlePersistenceProcessor> candleProcessors;

    public PersistenceProcessorFactory(PriceConfiguration configuration, RepositoryContainer repositoryContainer) {
        List<SaveRepository> repositories = repositoryContainer.getSaveRepositories();
        candleProcessors = new ArrayList<>();
        for (SaveRepository repository : repositories) {
            CandlePersistenceProcessor candleProcessor = new CandlePersistenceProcessor(repository, configuration);
            candleProcessor.start();
            candleProcessors.add(candleProcessor);
        }
    }

    public void close() throws Exception {
        for (CandlePersistenceProcessor processor : candleProcessors) {
            processor.close();
        }
    }
}
