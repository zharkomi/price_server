package com.price.common.db;

public interface RepositoryRegistry {
    String getName();
    Class<? extends SaveRepository> getSaveRepositoryClass();
    Class<? extends QueryRepository> getQueryRepositoryClass();
}
