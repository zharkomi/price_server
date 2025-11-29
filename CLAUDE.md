# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Java 21 market data aggregation server that collects real-time price data from cryptocurrency exchanges (currently Binance) and aggregates it into OHLCV (Open, High, Low, Close, Volume) candles at configurable timeframes. The system uses high-performance concurrent data structures (LMAX Disruptor) for low-latency event processing.

## Build and Run Commands

```bash
# Build the project
./gradlew build

# Run tests
./gradlew test

# Run the application (requires environment variables - see Configuration)
./gradlew run

# Clean build artifacts
./gradlew clean
```

### Testing

- Tests use JUnit 5 and Mockito for unit testing
- Test classes follow naming convention: `{ClassName}Test.java`
- Use `@ExtendWith(MockitoExtension.class)` for Mockito integration
- Mock dependencies with `@Mock` annotation
- Example test structure in `CandleAggregatorTest.java` shows comprehensive event handling tests

## Configuration

The application is configured entirely via environment variables:

**Required:**
- `ps.instruments` - Comma-separated list of instruments in format `NAME@SOURCE` (e.g., `BTCUSDT@BINANCE,ETHUSDT@BINANCE`)
- `ps.timeframe.{NAME}@{SOURCE}` - Comma-separated timeframes for each instrument (e.g., `ps.timeframe.BTCUSDT@BINANCE=1m,5m,1h`)
  - Timeframe format: number + unit (s=seconds, m=minutes, h=hours, d=days)

**Optional:**
- `ps.buffer.size` - LMAX Disruptor ring buffer size (default: 4096, must be power of 2)
- `ps.clickhouse.url` - ClickHouse JDBC URL (default: `jdbc:clickhouse://localhost:8123`)
- `ps.clickhouse.user` - ClickHouse username (default: `default`)
- `ps.clickhouse.password` - ClickHouse password (default: empty string)

Example:
```bash
export ps.instruments="BTCUSDT@BINANCE,ETHUSDT@BINANCE"
export ps.timeframe.BTCUSDT@BINANCE="1m,5m,15m"
export ps.timeframe.ETHUSDT@BINANCE="1m,5m"
export ps.buffer.size=8192
```

## Architecture

### High-Level Data Flow

```
WebSocket (Binance) → Connector → MarketDataProcessor → Input Disruptor → CandleAggregator(s)
                                           ↑                                         ↓
                                    NonDriftingTimer                         CandleProcessor
                                                                                     ↓
                                                                             Output Disruptor
                                                                                     ↓
                                                                          ClickHouse Repository
```

### Dual-Buffer Architecture

The system uses two segregated Disruptor ring buffers for optimal performance:

**Input Layer (per instrument):**
- Each `MarketDataProcessor` has its own Disruptor ring buffer
- Handles high-frequency market data events without storage latency
- Chain of `CandleAggregator` handlers process events for each timeframe sequentially

**Output Layer (shared):**
- `CandleProcessor` has a separate Disruptor ring buffer shared across all instruments
- Single multi-producer ring buffer → single consumer (Repository)
- Batches writes to storage efficiently using end-of-batch detection
- Complete isolation: storage backpressure never blocks market data ingestion

This architecture achieves 100K+ candles/second write throughput while maintaining sub-millisecond market data processing latency.

### Key Components

**Server** (`Server.java`)
- Entry point that orchestrates initialization of all components
- Creates one `MarketDataProcessor` per instrument
- Sets up shutdown hooks for graceful cleanup

**Configuration** (`Configuration.java`)
- Parses environment variables to build instrument list with their sources and timeframes
- Each instrument can have multiple timeframes (e.g., 1m, 5m, 1h candles)

**ConnectorFactory** (`ConnectorFactory.java`)
- Creates one `Connector` instance per unique `Source` (not per instrument)
- Multiple instruments from the same source share a single connector/WebSocket connection

**Connector** (`BinanceConnector.java`)
- Manages WebSocket connection to exchange
- Receives book ticker data (best bid/ask), calculates mid-price
- Routes market data events to appropriate `MarketDataProcessor` based on symbol

**MarketDataProcessor** (`MarketDataProcessor.java`)
- One instance per instrument
- Uses LMAX Disruptor for lock-free event processing
- Creates a chain of `CandleAggregator` handlers, one per timeframe
- Receives two event types: DATA (market updates) and TIMER (periodic sync)

**CandleAggregator** (`CandleAggregator.java`)
- Disruptor event handler that aggregates tick data into OHLCV candles
- Maintains state for current candle (open, high, low, close, volume)
- Flushes completed candle on:
  - Timestamp crosses candle boundary (new candle period starts)
  - Timer event fires (ensures candles are emitted even without recent trades)

**NonDriftingTimer** (`NonDriftingTimer.java`)
- Sends periodic TIMER events to all `MarketDataProcessor` instances
- Fires at exact second boundaries to prevent time drift
- Ensures candles are closed/emitted even during low-volume periods

**CandleProcessor** (`CandleProcessor.java`)
- Bridge between aggregation and storage layers
- Has its own Disruptor ring buffer (output layer)
- Receives completed candles from all `CandleAggregator` instances
- Publishes to `Repository` using batch detection for efficient writes

**Repository** (`ClickHouseRepository.java`)
- Persists completed candles to ClickHouse database
- Implements `EventHandler<CandleEvent>` to consume from output Disruptor
- Uses batch insertion with Disruptor's `isEndOfBatch()` for optimal throughput
- Automatically creates database schema on first run

**HistoryService** (`HistoryService.java`)
- HTTP REST API server using embedded Jetty (port 8080)
- Provides `/health` endpoint for service monitoring
- Provides `/history` endpoint for querying historical candle data
- Returns JSON responses with array-based OHLCV format

### Concurrency Model

**Input Layer:**
- One WebSocket thread per source produces market data events
- Each `MarketDataProcessor` has its own Disruptor ring buffer (single producer)
- Multiple `CandleAggregator` handlers chained using `.then()` for sequential processing per timeframe
- Wait strategy: `YieldingWaitStrategy` for low-latency event processing

**Output Layer:**
- Multiple `CandleAggregator` instances publish to shared output Disruptor (multi-producer)
- Single `Repository` consumer for batch writes to ClickHouse
- Disruptor's `isEndOfBatch()` detection enables efficient batching of database operations

### Adding New Exchange Support

1. Add new value to `Source` enum with `createConnector()` implementation
2. Create new connector class implementing `Connector` interface
3. Implement `register()`, `start()`, and `close()` methods
4. Convert exchange-specific data format to `MarketDataEvent`

## Storage Layer

### ClickHouse Schema

**Database:** `prices_db` (auto-created)

**Table:** `trade_candles`
```sql
CREATE TABLE trade_candles (
    instrument       LowCardinality(String),  -- "BTCUSDT@BINANCE"
    timeframe_ms     UInt32,                   -- Timeframe in milliseconds
    time             UInt64,                   -- Unix epoch in ms
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

**Key Features:**
- `ReplacingMergeTree` engine automatically deduplicates rows with same ORDER BY key
- Partitioned by instrument, timeframe, and date for efficient queries
- `LowCardinality` optimizes storage for repeated instrument strings
- Automatic schema initialization on first run

### Adding Alternative Storage

To implement a different storage backend:
1. Implement the `Repository` interface (extends `EventHandler<CandleEvent>`)
2. Add new repository class (e.g., `PostgresRepository`, `ParquetRepository`)
3. Update `RepositoryFactory.createRepository()` to support the new type
4. Add corresponding configuration environment variables

## REST API

The server exposes HTTP endpoints via `HistoryService` on port 8080:

**GET /health**
- Health check endpoint
- Returns: `{"status": "ok", "service": "HistoryService", "timestamp": <ms>}`

**GET /history**
- Query historical OHLCV candles
- Required params: `symbol`, `interval`, `from` (seconds), `to` (seconds)
- Example: `/history?symbol=BTCUSDT@BINANCE&interval=1m&from=1735516800&to=1735520400`
- Response format:
```json
{
  "success": true,
  "error": null,
  "times": [1735516800, 1735516860, ...],
  "opens": [42000.5, 42010.2, ...],
  "highs": [42050.0, 42030.5, ...],
  "lows": [41990.0, 42000.0, ...],
  "closes": [42010.2, 42005.8, ...],
  "volumes": [150000, 125000, ...]
}
```

**Input Validation:**
- `HistoryRequest` class validates all query parameters
- Missing or invalid parameters return 400 with descriptive error message
- Array-based format optimized for charting libraries

## Important Notes

- Disruptor buffer size must be a power of 2
- Timer events are critical for candle emission during low-volume periods
- Each MarketDataProcessor processes events for a single instrument only
- Price and volume use `double` and `long` types for precision (not `float`)
- Timeframe parsing is in milliseconds internally (configuration uses human-readable format)
- Graceful shutdown via shutdown hooks closes all WebSocket connections and flushes remaining candles
- After creating new file add it to git