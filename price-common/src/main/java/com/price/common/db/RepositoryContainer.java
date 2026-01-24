package com.price.common.db;

import com.price.common.config.DataBase;
import com.price.common.config.PriceConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class RepositoryContainer implements AutoCloseable {
    private final PriceConfiguration configuration;

    private final List repositories = new ArrayList<>();
    private final AutowireCapableBeanFactory beanFactory;
    private final Map<String, Class<?>> saveRepositoryClasses = new ConcurrentHashMap<>();
    private final Map<String, Class<?>> queryRepositoryClasses = new ConcurrentHashMap<>();

    public RepositoryContainer(PriceConfiguration configuration,
                               ApplicationContext applicationContext,
                               List<RepositoryRegistry> repositoryRegistries) {
        this.configuration = configuration;
        this.beanFactory = applicationContext.getAutowireCapableBeanFactory();
        for (RepositoryRegistry registry : repositoryRegistries) {
            saveRepositoryClasses.put(registry.getName(), registry.getSaveRepositoryClass());
            queryRepositoryClasses.put(registry.getName(), registry.getQueryRepositoryClass());
        }
    }

    public List<SaveRepository> getSaveRepositories() {
        return getRepositories(saveRepositoryClasses);
    }

    public List<QueryRepository> getQueryRepositories() {
        return getRepositories(queryRepositoryClasses);
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> getRepositories(Map<String, Class<?>> classMap) {
        if (repositories.isEmpty()) {
            for (DataBase dataBase : configuration.dataBases()) {
                Class<?> repositoryClazz = classMap.get(dataBase.type());
                repositories.add(beanFactory.getBean(repositoryClazz, dataBase));
            }
        }
        return repositories;
    }

    @Override
    public void close() throws Exception {

    }
}
