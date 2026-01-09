package com.price.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;

public class FileConfigurationReader {

    public static final String ENV_CONFIG_FILE = "CONFIG_FILE";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String filePath;

    public FileConfigurationReader(String filePath) {
        this.filePath = filePath;
    }

    public Configuration read() {
        try {
            return MAPPER.readValue(Path.of(filePath).toFile(), Configuration.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read configuration file: " + filePath, e);
        }
    }
}