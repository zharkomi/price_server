# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Java 21 multi-module market data aggregation system that collects real-time price data from cryptocurrency exchanges (currently Binance) and aggregates it into OHLCV (Open, High, Low, Close, Volume) candles at configurable timeframes. The system uses high-performance concurrent data structures (LMAX Disruptor) for low-latency event processing.

## Module Structure

| Module | Type | Description |
|--------|------|-------------|
| `price-common` | Library | Shared configuration, interfaces, data models |
| `price-stream` | Application | Real-time streaming - WebSocket listener, aggregation, storage, WebSocket API |
| `price-query` | Spring Boot | REST API for historical data queries |
| `scripts` | Python | Utility scripts for testing and visualization |

## Build and Run Commands

```bash
# Build all modules
./gradlew build

# Build specific module
./gradlew :price-stream:build
./gradlew :price-query:build
./gradlew :price-common:build

# Run tests
./gradlew test

# Create deployable JARs
./gradlew :price-stream:fatJar
./gradlew :price-query:bootJar

# Run applications
CONFIG_FILE=config/config.json ./gradlew :price-stream:run
./gradlew :price-query:bootRun

# Clean build artifacts
./gradlew clean

# Docker
docker compose up --build -d
docker compose down
```

### Testing

- Tests use JUnit 5 and Mockito for unit testing
- Test classes follow naming convention: `{ClassName}Test.java`
- Use `@ExtendWith(MockitoExtension.class)` for Mockito integration
- Mock dependencies with `@Mock` annotation
- Stream tests: `price-stream/src/test/java/com/price/market/CandleAggregatorTest.java`
- Query tests: `price-query/src/test/java/com/price/query/controller/HistoryControllerTest.java`

## Configuration

Configuration can be provided via JSON file or environment variables.

### JSON File Configuration (Recommended)

Set `CONFIG_FILE` environment variable to point to config file:

```json
{
  "instruments": [
    {"name": "BTCUSDT", "source": "BINANCE", "timeframes": ["1m", "5m", "15m", "1h"]},
    {"name": "ETHUSDT", "source": "BINANCE", "timeframes": ["5s", "10s", "5m"]}
  ],
  "dataBases": [
    {
      "type": "com.price.db.ClickHouseRepository",
      "url": "jdbc:clickhouse://localhost:8123",
      "user": "default",
      "password": ""
    }
  ],
  "httpPort": 8080,
  "disruptorBufferSize": 4096
}
```

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `CONFIG_FILE` | Path to JSON config file | - |
| `ps.instruments` | Comma-separated instruments (e.g., `BTCUSDT@BINANCE`) | - |
| `ps.timeframe.{SYMBOL}@{SOURCE}` | Timeframes for instrument | - |
| `ps.buffer.size` | Disruptor ring buffer size (power of 2) | 4096 |
| `ps.http.port` | HTTP server port | 8080 |
| `ps.clickhouse.url` | ClickHouse JDBC URL | `jdbc:clickhouse://localhost:8123` |
| `ps.clickhouse.user` | ClickHouse username | `default` |
| `ps.clickhouse.password` | ClickHouse password | `` |
| `ps.repository.type` | Repository class name | `com.price.db.ClickHouseRepository` |

### Timeframe Format
- `s` = seconds, `m` = minutes, `h` = hours, `d` = days
- Examples: `5s`, `1m`, `15m`, `1h`, `4h`, `1d`

## Architecture

### High-Level Data Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              PRICE-STREAM                                    │
│                                                                              │
│  ┌──────────────┐    ┌─────────────────────┐    ┌─────────────────────────┐ │
│  │   Binance    │───▶│  MarketDataProcessor │───▶│ CandlePersistenceProc.  │ │
│  │  WebSocket   │    │   (Input Disruptor)  │    │   (Output Disruptor)    │ │
│  └──────────────┘    └─────────────────────┘    └───────────┬─────────────┘ │
│                               │                              │               │
│                      ┌────────┴────────┐                     │               │
│                      ▼                 ▼                     ▼               │
│             ┌─────────────────┐  ┌──────────────┐   ┌──────────────┐        │
│             │  StreamService  │  │ClientNotifier│   │  ClickHouse  │        │
│             │   (WebSocket)   │  │              │   │  Repository  │        │
│             └────────┬────────┘  └──────────────┘   └──────────────┘        │
└──────────────────────┼──────────────────────────────────────┼───────────────┘
                       │                                       │
                       ▼                                       │
              WebSocket Clients                                │
                                                              ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                              PRICE-QUERY                                     │
│  ┌──────────────┐    ┌─────────────────┐    ┌──────────────────────────────┐│
│  │  REST API    │◀──▶│  HistoryService │◀──▶│  ClickHouseQueryRepository   ││
│  │ /history     │    │                 │    │        (HikariCP)            ││
│  └──────────────┘    └─────────────────┘    └──────────────────────────────┘│
└─────────────────────────────────────────────────────────────────────────────┘
```

### Dual-Buffer Architecture

The system uses two segregated Disruptor ring buffers for optimal performance:

**Input Layer (per instrument):**
- Each `MarketDataProcessor` has its own Disruptor ring buffer
- Single producer (WebSocket) → chain of consumers (CandleAggregators)
- Handles high-frequency market data without storage latency
- `YieldingWaitStrategy` for low-latency processing

**Output Layer (shared):**
- `CandlePersistenceProcessor` has a separate Disruptor per repository
- Multi-producer (all CandleAggregators) → single consumer (Repository)
- Batches writes using `isEndOfBatch()` detection
- Complete isolation: storage backpressure never blocks market data

### Module: price-common

Shared library with configuration and interfaces.

| Package | Key Classes | Description |
|---------|-------------|-------------|
| `com.price.common.config` | `PriceConfiguration`, `Instrument`, `DataBase` | Configuration records |
| `com.price.common.config` | `PropertyConfigurationReader`, `FileConfigurationReader` | Config loaders |
| `com.price.common.db` | `Candle`, `CandleEvent` | Data models |
| `com.price.common.db` | `SaveRepository`, `QueryRepository` | Storage interfaces |
| `com.price.common.db` | `RepositoryFactory<T>` | Reflection-based factory |
| `com.price.common` | `Source`, `Util`, `TraceableEvent` | Enums and utilities |

### Module: price-stream

Real-time streaming server with WebSocket API.

| Package | Key Classes | Description |
|---------|-------------|-------------|
| `com.price.stream` | `StreamServer` | Entry point, orchestrates components |
| `com.price.stream.market` | `MarketDataProcessor` | Per-instrument Disruptor processor |
| `com.price.stream.market` | `CandleAggregator` | OHLCV aggregation logic |
| `com.price.stream.market` | `NonDriftingTimer` | Sync timer at second boundaries |
| `com.price.stream.market` | `ClientNotifier` | Routes events to WebSocket clients |
| `com.price.stream.market` | `Connector` | Connector interface |
| `com.price.stream.market.source` | `BinanceConnector`, `ConnectorFactory` | Exchange connectors |
| `com.price.stream.storage` | `CandlePersistenceProcessor` | Output Disruptor bridge |
| `com.price.stream.service` | `StreamService` | Netty WebSocket server |
| `com.price.stream.service` | `ClientConnectionHandler` | WebSocket frame handler |
| `com.price.stream.service` | `SubscriptionProcessor` | Per-client subscriptions |
| `com.price.stream.common` | `CandleProcessor`, `SubscriptionKey` | Interfaces and records |
| `com.price.stream.event.buffer` | `MarketDataEvent` | Internal Disruptor event |
| `com.price.stream.event.client` | `InstrumentEvent`, `SubscriptionEvent` | Client protocol events |
| `com.price.db` | `ClickHouseRepository` | SaveRepository implementation |

### Module: price-query

Spring Boot REST API service.

| Package | Key Classes | Description |
|---------|-------------|-------------|
| `com.price.query` | `QueryServer` | Spring Boot entry point |
| `com.price.query` | `ApplicationContext` | Bean configuration |
| `com.price.query.controller` | `HistoryController` | REST endpoint |
| `com.price.query.service` | `HistoryService` | Business logic |
| `com.price.query.dto` | `HistoryResponse` | Response DTO |
| `com.price.db` | `ClickHouseRepository` | QueryRepository implementation |

## REST API (price-query)

**GET /history**
- Query historical OHLCV candles
- Params: `symbol`, `interval`, `from` (seconds), `to` (seconds)
- Example: `/history?symbol=BTCUSDT@BINANCE&interval=1m&from=1735516800&to=1735520400`

```json
{
  "s": "ok",
  "errmsg": null,
  "t": [1735516800, 1735516860],
  "o": [42000.5, 42010.2],
  "h": [42050.0, 42030.5],
  "l": [41990.0, 42000.0],
  "c": [42010.2, 42005.8],
  "v": [150000, 125000]
}
```

## WebSocket Streaming API (price-stream)

Connect to `ws://host:8081/stream` (port = httpPort + 1)

**Subscribe:**
```json
{"type": "SUBSCRIBE", "instrument": "BTCUSDT@BINANCE", "timeframe": 60000}
```

**Unsubscribe:**
```json
{"type": "UNSUBSCRIBE", "instrument": "BTCUSDT@BINANCE", "timeframe": 60000}
```

**Receive (batched on TIMER events):**
```json
[{"i": "BTCUSDT@BINANCE", "t": 1735516800000, "f": 60000, "o": 42000.5, "h": 42050.0, "l": 41990.0, "c": 42010.2, "v": 150000}]
```

## Storage Layer

### ClickHouse Schema

**Database:** `prices_db` (auto-created)

**Table:** `trade_candles`
```sql
CREATE TABLE trade_candles (
    instrument       LowCardinality(String),
    timeframe_ms     UInt32,
    time             UInt64,
    open             Float64,
    high             Float64,
    low              Float64,
    close            Float64,
    volume           Float64
)
ENGINE = ReplacingMergeTree()
PARTITION BY (instrument, timeframe_ms, toDate(time / 1000))
ORDER BY (instrument, timeframe_ms, time)
```

### Adding Alternative Storage

1. Implement `SaveRepository` (for stream) or `QueryRepository` (for query) interface
2. Add constructor accepting `DataBase` configuration record
3. Specify class name in config: `"type": "com.price.db.NewRepository"`

## Adding New Exchange Support

1. Add value to `Source` enum in `price-common`
2. Create connector class in `price-stream` implementing `Connector` interface:
   ```java
   public class NewExchangeConnector implements Connector {
       void register(MarketDataProcessor processor);
       void start();
       void close();
   }
   ```
3. Update `ConnectorFactory` to handle new source
4. Convert exchange data to `MarketDataEvent` (DATA type with price/volume)

## Key Implementation Notes

- Disruptor buffer size must be a power of 2
- Timer events are critical for candle emission during low-volume periods
- Each MarketDataProcessor processes events for a single instrument only
- Price uses `double`, volume uses `long` for precision
- Timeframes stored as milliseconds internally
- WebSocket streaming port = httpPort + 1
- Stream and Query modules have separate ClickHouseRepository implementations
- RepositoryFactory uses reflection to instantiate repositories from config
- After creating new files, add them to git
