# Price Common

Shared library containing configuration management, data models, and storage interfaces used by both `price-stream` and `price-query` modules.

## Components

### Configuration (`com.price.common.config`)

| Class | Type | Description |
|-------|------|-------------|
| `PriceConfiguration` | Record | Main configuration container with instruments, databases, httpPort, disruptorBufferSize |
| `Instrument` | Record | Trading instrument definition (name, source, timeframes, metrics) |
| `DataBase` | Record | Database connection configuration (type, url, user, password) |
| `PropertyConfigurationReader` | Class | Reads configuration from environment variables (`ps.*` pattern) |
| `FileConfigurationReader` | Class | Reads configuration from JSON file (via `CONFIG_FILE` env var) |

### Storage Interfaces (`com.price.common.storage`)

| Interface/Class | Type | Description |
|-----------------|------|-------------|
| `Candle` | Record | OHLCV data structure (instrument, timeframeMs, time, open, high, low, close, volume) |
| `CandleEvent` | Class | Mutable Disruptor event extending `TraceableEvent` with fluent accessors |
| `SaveRepository` | Interface | Extends `EventHandler<CandleEvent>` for insert operations (used by stream) |
| `QueryRepository` | Interface | Query interface for retrieving candles by instrument/timeframe/time range |
| `RepositoryFactory<T>` | Class | Generic factory using reflection to instantiate repository implementations |

### Utilities (`com.price.common`)

| Class | Description |
|-------|-------------|
| `Source` | Enum of supported exchanges (currently `BINANCE`) |
| `Util` | Timeframe parsing utilities (e.g., "1m" → 60000ms) |
| `TraceableEvent` | Base class for traceable events with sequence tracking |

## Usage

### As a Dependency

Both `price-stream` and `price-query` include this module:

```groovy
dependencies {
    implementation project(':price-common')
}
```

### Configuration Loading

```java
// From JSON file
PriceConfiguration config = FileConfigurationReader.read();

// From environment variables
PriceConfiguration config = PropertyConfigurationReader.read();
```

### Repository Factory

The `RepositoryFactory` uses reflection to instantiate repository implementations from configuration:

```java
// Create factory for SaveRepository (stream module)
RepositoryFactory<SaveRepository> factory = new RepositoryFactory<>(
    SaveRepository.class,
    config.dataBases()
);
List<SaveRepository> repositories = factory.createRepositories();

// Create factory for QueryRepository (query module)
RepositoryFactory<QueryRepository> factory = new RepositoryFactory<>(
    QueryRepository.class,
    config.dataBases()
);
QueryRepository repository = factory.createRepositories().get(0);
```

Repository implementations must have a constructor accepting `DataBase`:

```java
public class ClickHouseRepository implements SaveRepository, QueryRepository {
    public ClickHouseRepository(DataBase config) {
        // Initialize connection
    }
}
```

## Package Structure

```
com.price.common
├── config
│   ├── PriceConfiguration.java    # Main config record
│   ├── Instrument.java            # Instrument definition
│   ├── DataBase.java              # DB connection config
│   ├── PropertyConfigurationReader.java
│   └── FileConfigurationReader.java
├── storage
│   ├── Candle.java                # OHLCV record
│   ├── CandleEvent.java           # Disruptor event
│   ├── SaveRepository.java        # Write interface
│   ├── QueryRepository.java       # Read interface
│   └── RepositoryFactory.java     # Reflection-based factory
├── Source.java                    # Exchange enum
├── Util.java                      # Timeframe utilities
└── TraceableEvent.java            # Base event class
```

## Dependencies

- Jackson Databind - JSON parsing for file configuration
- LMAX Disruptor - Event interfaces (`EventHandler`)
- Commons Lang3 - Utility functions
- Log4j2 - Logging
