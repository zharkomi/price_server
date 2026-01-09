package com.price.common.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.price.market.source.Source;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Configuration(List<Instrument> instruments,
                            List<DataBase> dataBases,
                            int httpPort,
                            int disruptorBufferSize) {

    public static Configuration read() {
        String configFile = System.getenv(FileConfigurationReader.ENV_CONFIG_FILE);
        if (configFile != null && !configFile.trim().isEmpty()) {
            return new FileConfigurationReader(configFile).read();
        }
        return new PropertyConfigurationReader().read();
    }

    public List<Source> getSources() {
        return this.instruments.stream().map(Instrument::source).distinct().toList();
    }
}
