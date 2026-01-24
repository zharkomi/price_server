# Extension Modules

This folder contains extension modules that implement pluggable storage backends and exchange connectors.

## Existing Modules

### price-db-clickhouse

Database storage module that persists OHLCV candle data to ClickHouse.

| Class | Description |
|-------|-------------|
| `SaveClickhouseRepository` | Implements `SaveRepository` - writes candle events from Disruptor ring buffer |
| `QueryClickhouseRepository` | Implements `QueryRepository` - handles historical data queries via REST API |
| `ClickhouseRegistry` | Implements `RepositoryRegistry` - registers module for auto-discovery |

### price-source-binance

Exchange connector module that streams real-time price data from Binance.

| Class | Description |
|-------|-------------|
| `Connector` | Implements `Connector` interface - WebSocket client for Binance bookTicker stream |

---

## Implementing a Database Module

### Required Interfaces

**SaveRepository** (`com.price.common.db`) - Write path for streaming candle data:
```java
public interface SaveRepository extends EventHandler<CandleEvent>, AutoCloseable {
    void onEvent(CandleEvent event, long sequence, boolean endOfBatch) throws Exception;
}
```

**QueryRepository** (`com.price.common.db`) - Read path for historical queries:
```java
public interface QueryRepository extends AutoCloseable {
    List<Candle> queryCandles(String instrument, int timeframeMs,
                              long fromTimestamp, long toTimestamp) throws Exception;
}
```

**RepositoryRegistry** (`com.price.common.db`) - Module registration:
```java
public interface RepositoryRegistry {
    String getName();                                        // Must match "type" in config
    Class<? extends SaveRepository> getSaveRepositoryClass();
    Class<? extends QueryRepository> getQueryRepositoryClass();
}
```

### Data Models

| Model | Description |
|-------|-------------|
| `CandleEvent` | Mutable event: `instrument`, `timeframeMs`, `time`, `open`, `high`, `low`, `close`, `volume` |
| `Candle` | Immutable record with same fields, returned from queries |
| `DataBase` | Config record: `type`, `url`, `user`, `password` |

### Implementation Steps

1. Create module folder: `modules/price-db-{name}/`
2. Add `build.gradle` with dependency on `project(':price-common')` and `libs.disruptor`
3. Implement `SaveRepository` with `@Repository` and `@Scope("prototype")` annotations
   - Constructor must accept `DataBase` parameter
   - Use `endOfBatch` flag to batch writes for performance
4. Implement `QueryRepository` with `@Repository` and `@Scope("prototype")` annotations
   - Use connection pooling (HikariCP) for query performance
5. Implement `RepositoryRegistry` with `@Component` and `@Order(n)` annotations
   - `getName()` must match `type` field in configuration
6. Add module to `settings.gradle`: `include 'modules:price-db-{name}'`
7. Configure: `{"dataBases": [{"type": "{name}", "url": "...", "user": "...", "password": "..."}]}`

---

## Implementing a Source Connector Module

### Required Interfaces

**Connector** (`com.price.common.source`) - Exchange WebSocket client:
```java
public interface Connector extends AutoCloseable {
    void start();
    void register(PriceEventHandler handler);
}
```

**PriceEventHandler** (`com.price.common.source`) - Callback provided by system:
```java
public interface PriceEventHandler {
    Instrument getInstrument();
    void handlePriceEvent(long timestamp, double price, long volume);
}
```

### Data Models

| Model | Description |
|-------|-------------|
| `Instrument` | Config record: `name` (symbol), `source` (exchange), `timeframes[]` |

### Implementation Steps

1. Create module folder: `modules/price-source-{exchange}/`
2. Add `build.gradle` with dependency on `project(':price-common')` and exchange SDK
3. Implement `Connector` with `@Service` annotation
   - Store handlers in `ConcurrentHashMap<String, PriceEventHandler>` by symbol
   - `register()` is called before `start()` for each configured instrument
   - Route incoming price data to correct handler by symbol
4. Add module to `settings.gradle`: `include 'modules:price-source-{exchange}'`
5. Configure: `{"instruments": [{"name": "BTCUSDT", "source": "{EXCHANGE}", "timeframes": ["1m"]}]}`

---

## Best Practices

**Database Modules:**
- Use `endOfBatch` flag to batch writes
- Use HikariCP for query repository connection pooling
- Use `@Scope("prototype")` for multiple database configurations
- Implement `close()` to flush pending data

**Source Connectors:**
- Use `ConcurrentHashMap` for thread-safe handler storage
- Prefer single WebSocket with combined streams
- Use exchange timestamp when available, otherwise `System.currentTimeMillis()`