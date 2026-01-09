package com.price.stream.storage.db;

import com.price.stream.common.config.Configuration;
import com.price.stream.common.config.DataBase;
import com.price.stream.storage.Repository;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class RepositoryFactory implements AutoCloseable {
    private final Configuration configuration;
    private List<Repository> repositories = new ArrayList<>();

    public RepositoryFactory(Configuration configuration) {
        this.configuration = configuration;
    }

    public List<Repository> getRepositories() {
        if (repositories.isEmpty()) {
            for (DataBase dataBase : configuration.dataBases()) {
                log.info("Creating repository: {}", dataBase);
                try {
                    repositories.add((Repository) Class.forName(dataBase.type())
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
        for (Repository repository : repositories) {
            log.info("Closing repository: {}", repository.getClass().getName());
            repository.close();
        }
        repositories.clear();
    }
}
