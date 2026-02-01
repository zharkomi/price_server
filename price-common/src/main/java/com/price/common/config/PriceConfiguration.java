package com.price.common.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@JsonIgnoreProperties(ignoreUnknown = true)
public record PriceConfiguration(List<Instrument> instruments,
                                 List<DataBase> dataBases,
                                 int httpPort,
                                 int marketDataBufferSize,
                                 int clientBufferSize,
                                 boolean aggregateEvents) {

    public static PriceConfiguration read() {
        String configFile = System.getenv(FileConfigurationReader.ENV_CONFIG_FILE);
        if (configFile != null && !configFile.trim().isEmpty()) {
            log.info("Reading configuration from file: {}", configFile);
            return new FileConfigurationReader(configFile).read();
        }
        log.info("Reading configuration from application properties");
        return new PropertyConfigurationReader().read();
    }

    public List<String> getSources() {
        return this.instruments.stream().map(Instrument::source).distinct().toList();
    }
}
