package com.price.common.storage;

import com.price.common.config.PriceConfiguration;
import com.price.common.config.DataBase;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class RepositoryFactory<T extends AutoCloseable> implements AutoCloseable {
    private final PriceConfiguration configuration;

    private final List<T> repositories = new ArrayList<>();

    public RepositoryFactory(PriceConfiguration configuration) {
        this.configuration = configuration;
    }

    @SuppressWarnings("unchecked")
    public List<T> getRepositories() {
        if (repositories.isEmpty()) {
            for (DataBase dataBase : configuration.dataBases()) {
                log.info("Creating repository: {}", dataBase);
                try {
                    repositories.add((T) Class.forName(dataBase.type())
                            .getConstructor(DataBase.class)
                            .newInstance(dataBase));
                } catch (Exception e) {
                    throw new IllegalArgumentException("Failed to create repository " + dataBase.type(), e);
                }
            }
        }
        return repositories;
    }

    @Override
    public void close() throws Exception {
        for (T repository : repositories) {
            log.info("Closing repository: {}", repository.getClass().getName());
            repository.close();
        }
        repositories.clear();
    }
}
