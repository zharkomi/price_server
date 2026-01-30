# Price Common

Shared library containing configuration management, data models, storage interfaces, and source connector interfaces used by both `price-stream` and `price-query` modules.

## Components

### Configuration (`com.price.common.config`)

| Class | Type | Description |
|-------|------|-------------|
| `PriceConfiguration` | Record | Main configuration container with instruments, databases, httpPort, marketDataBufferSize, clientBufferSize, aggregateEvents |
| `Instrument` | Record | Trading instrument definition (name, source, timeframes, metrics accumulators) |
| `DataBase` | Record | Database connection configuration (type, url, user, password) |
| `PropertyConfigurationReader` | Class | Reads configuration from environment variables (`ps.*` pattern) |
| `FileConfigurationReader` | Class | Reads configuration from JSON file (via `CONFIG_FILE` env var) |

### Storage Interfaces (`com.price.common.db`)

| Interface/Class | Type | Description |
|-----------------|------|-------------|
| `Candle` | Record | OHLCV data structure (instrument, timeframeMs, time, open, high, low, close, volume) |
| `CandleEvent` | Class | Mutable Disruptor event extending `TraceableEvent` with JSON serialization |
| `SaveRepository` | Interface | Extends `EventHandler<CandleEvent>` for insert operations (used by stream) |
| `QueryRepository` | Interface | Query interface for retrieving candles by instrument/timeframe/time range |
| `RepositoryRegistry` | Interface | Module registration for auto-discovery of database implementations |
| `RepositoryContainer` | Service | Spring-managed factory that instantiates repositories based on configuration |

### Source Interfaces (`com.price.common.source`)

| Interface | Description |
|-----------|-------------|
| `Connector` | Exchange connector interface with `start()` and `register(handler)` methods |
| `PriceEventHandler` | Callback interface for receiving price events from connectors |

### Utilities (`com.price.common`)

| Class | Description |
|-------|-------------|
| `Util` | Timeframe parsing utilities (e.g., "1m" -> 60000ms) |
| `TraceableEvent` | Base class for traceable events with nanosecond timestamp tracking |

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
// Auto-detect: uses JSON file if CONFIG_FILE is set, otherwise env variables
PriceConfiguration config = PriceConfiguration.read();

// Explicitly from JSON file
PriceConfiguration config = new FileConfigurationReader("/path/to/config.json").read();

// Explicitly from environment variables
PriceConfiguration config = new PropertyConfigurationReader().read();
```

### Repository Container

The `RepositoryContainer` uses Spring dependency injection to instantiate repository implementations:

```java
@Service
public class MyService {
    private final RepositoryContainer repositoryContainer;

    public MyService(RepositoryContainer repositoryContainer) {
        this.repositoryContainer = repositoryContainer;
    }

    public void process() {
        List<SaveRepository> saveRepos = repositoryContainer.getSaveRepositories();
        List<QueryRepository> queryRepos = repositoryContainer.getQueryRepositories();
    }
}
```

Database modules register via `RepositoryRegistry`:

```java
@Component
@Order(1)
public class ClickhouseRegistry implements RepositoryRegistry {
    @Override
    public String getName() {
        return "clickhouse";  // Matches "type" field in config
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
```

## Package Structure

```
com.price.common
├── config
│   ├── PriceConfiguration.java    # Main config record
│   ├── Instrument.java            # Instrument definition with metrics
│   ├── DataBase.java              # DB connection config
│   ├── PropertyConfigurationReader.java
│   └── FileConfigurationReader.java
├── db
│   ├── Candle.java                # OHLCV record
│   ├── CandleEvent.java           # Disruptor event with JSON support
│   ├── SaveRepository.java        # Write interface (EventHandler)
│   ├── QueryRepository.java       # Read interface
│   ├── RepositoryRegistry.java    # Module registration interface
│   └── RepositoryContainer.java   # Spring factory service
├── source
│   ├── Connector.java             # Exchange connector interface
│   └── PriceEventHandler.java     # Price event callback
├── Util.java                      # Timeframe utilities
└── TraceableEvent.java            # Base event class
```

## Dependencies

- Spring Context - Dependency injection and bean management
- Jackson Databind - JSON parsing for file configuration
- LMAX Disruptor - Event interfaces (`EventHandler`)
- Commons Lang3 - Utility functions
- Lombok - Boilerplate reduction
- Log4j2 - Logging
