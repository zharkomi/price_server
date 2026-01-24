package com.price.db.clickhouse;

import com.price.common.db.QueryRepository;
import com.price.common.db.RepositoryRegistry;
import com.price.common.db.RepositoryContainer;
import com.price.common.db.SaveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(1)
@Component
@RequiredArgsConstructor
public class ClickhouseRegistry implements RepositoryRegistry {

    @Override
    public String getName() {
        return "clickhouse";
    }

    @Override
    public Class<? extends SaveRepository> getSaveRepositoryClass() {
        return SaveClickhouseRepository.class;
    }

    @Override
    public Class<? extends QueryRepository> getQueryRepositoryClass() {
        return QueryClickhouseRepository.class;
    }
}
