package com.price.query;

import com.price.common.storage.RepositoryFactory;
import com.price.common.config.PriceConfiguration;
import com.price.common.storage.QueryRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationContext {

    @Bean
    public RepositoryFactory<QueryRepository> getRepositoryFactory(PriceConfiguration configuration) {
        return new RepositoryFactory<>(configuration);
    }

    @Bean
    public PriceConfiguration getConfiguration() {
        return PriceConfiguration.read();
    }

    @Bean
    public QueryRepository getQueryRepository(RepositoryFactory<QueryRepository> repositoryFactory) {
        return repositoryFactory.getRepositories().getFirst();
    }
}
