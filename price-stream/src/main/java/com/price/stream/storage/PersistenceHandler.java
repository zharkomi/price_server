package com.price.stream.storage;

import com.price.common.config.PriceConfiguration;
import com.price.common.db.RepositoryContainer;
import com.price.common.db.SaveRepository;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class PersistenceHandler {

    @Getter
    private final List<CandlePersistenceProcessor> candleProcessors;

    public PersistenceHandler(PriceConfiguration configuration, RepositoryContainer repositoryContainer) {
        List<SaveRepository> repositories = repositoryContainer.getSaveRepositories();
        candleProcessors = new ArrayList<>();
        for (SaveRepository repository : repositories) {
            CandlePersistenceProcessor candleProcessor = new CandlePersistenceProcessor(repository, configuration);
            candleProcessors.add(candleProcessor);
        }
    }

    public void start() {
        candleProcessors.forEach(CandlePersistenceProcessor::start);
    }

    public void close() throws Exception {
        for (CandlePersistenceProcessor processor : candleProcessors) {
            processor.close();
        }
    }
}
