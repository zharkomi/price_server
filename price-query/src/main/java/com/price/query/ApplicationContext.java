package com.price.query;

import com.price.common.db.RepositoryContainer;
import com.price.common.config.PriceConfiguration;
import com.price.common.db.QueryRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationContext {

    @Bean
    public PriceConfiguration getConfiguration() {
        return PriceConfiguration.read();
    }

    @Bean(name = "queryRepository")
    public QueryRepository getQueryRepository(RepositoryContainer repositoryContainer) {
        return repositoryContainer.getQueryRepositories().getFirst();
    }
}
