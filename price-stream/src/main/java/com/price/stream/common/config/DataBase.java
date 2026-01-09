package com.price.stream.common.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DataBase(
        String type,
        String url,
        String user,
        String password
) {
}
