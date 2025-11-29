package com.price.storage.db;

import com.price.common.Configuration;
import com.price.storage.Repository;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RepositoryFactory implements AutoCloseable {
    private final Configuration configuration;
    private Repository repository;

    public RepositoryFactory(Configuration configuration) {
        this.configuration = configuration;
    }

    public Repository getRepository() {
        if (repository == null) {
            String type = configuration.repositoryType;
            log.info("Creating repository of type: {}", type);

            switch (type) {
                case "CLICKHOUSE":
                    repository = new ClickHouseRepository(configuration);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported repository type: " + type);
            }
        }
        return repository;
    }

    @Override
    public void close() throws Exception {
        if (repository != null) {
            log.info("Closing repository");
            repository.close();
            repository = null;
        }
    }
}
