# Price Server

A high-performance Java 21 market data aggregation system that collects real-time price data from exchanges and aggregates it into OHLCV (Open, High, Low, Close, Volume) candles at configurable timeframes.

## Project Structure

The project is split into modules:

| Module | Description |
|--------|-------------|
| [price-common](./price-common/README.md) | Shared library with configuration, interfaces, and data models |
| [price-stream](./price-stream/README.md) | Real-time streaming server - WebSocket listener, aggregation, storage |
| [price-query](./price-query/README.md) | REST API service for historical data queries |
| [price-ui](./price-ui/README.md) | Angular 18 web UI with TradingView Lightweight Charts |
| [modules](./modules/README.md) | Extension modules - pluggable storage backends and exchange connectors |
| [scripts](./scripts/README.md) | Python utility scripts for testing and visualization |

## Key Features

- **Real-time data collection** from cryptocurrency exchanges (Binance)
- **Multi-timeframe aggregation** - configure multiple timeframes per instrument
- **High-performance architecture** using LMAX Disruptor for lock-free event processing
- **WebSocket streaming** - subscribe to real-time candle updates via Netty WebSocket
- **REST API** - query historical OHLCV data with Spring Boot
- **ClickHouse storage** - high-performance columnar database for time-series data

## Quick Start

### Using Docker Compose (Recommended)

1. **Clone the repository**
   ```bash
   git clone https://github.com/zharkomi/price_server.git
   cd price_server
   ```

2. **Configure instruments** in `config/config.json`:

3. **Start services**
   ```bash
   docker compose up --build -d
   ```

4. **Verify**
   ```bash
   # Check services
   docker compose ps

   # Query historical data
   curl "http://localhost:8080/history?symbol=BTCUSDT@BINANCE&interval=1m&from=1735516800&to=1735520400"
   ```

5. **Run Web UI** (optional, requires Node.js 18+)
   ```bash
   cd price-ui
   npm install
   npm start
   ```
   Open `http://localhost:4200` in your browser.

6. **Stop**
   ```bash
   docker compose down
   ```

### Docker Services

| Service | Port | Description |
|---------|------|-------------|
| price-query | 8080 | REST API for historical queries |
| price-stream | 8081 | WebSocket streaming (httpPort + 1) |
| price-ui | 4200 | Angular web UI (development server) |
| clickhouse | 8123, 9000 | ClickHouse HTTP and native ports |

### Docker Multi-Stage Build

All modules use multi-stage Docker builds with BuildKit cache mounts:

```
┌─────────────────────────────────────────────────────────────────────────┐
│  Stage 1: BUILDER (eclipse-temurin:21-jdk-jammy)                        │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │  • Copy Gradle wrapper, build files, and source code              │  │
│  │  • BuildKit cache mount: /root/.gradle persists between builds    │  │
│  │  • Run: ./gradlew fatJar (stream) or bootJar (query)              │  │
│  │  • Result: Compiled application JAR                               │  │
│  └───────────────────────────────────────────────────────────────────┘  │
│                                    ↓                                    │
│  Stage 2: RUNTIME (eclipse-temurin:21-jre-jammy)                        │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │  • Copy only the JAR from builder                                 │  │
│  │  • JRE-only base image (no JDK, no build tools)                   │  │
│  │  • Optimized JVM flags for containers                             │  │
│  │  • Final image size: ~300MB                                       │  │
│  └───────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
```

To clear the cache: `docker builder prune`

**Benefits:**

| Benefit | Description |
|---------|-------------|
| **Smaller images** | ~300MB final image vs 1GB+ with full JDK |
| **Faster rebuilds** | Gradle cache persists; only changed code recompiles |
| **Security** | No build tools, compilers, or source code in production image |
| **Container-aware JVM** | Uses `-XX:+UseContainerSupport` and `-XX:MaxRAMPercentage=75.0` |

**JVM Configuration:**
```
-Xms500m -Xmx1g                      # Initial/max heap
-XX:+UseContainerSupport             # Respect container memory limits
-XX:MaxRAMPercentage=75.0            # Use 75% of container memory
-XX:+HeapDumpOnOutOfMemoryError      # Dump heap on OOM
-Xlog:gc*:file=/app/logs/gc.log      # GC logging with rotation
```

## Configuration

Configuration can be provided via JSON file or environment variables.

### JSON File Configuration

Set `CONFIG_FILE` environment variable to point to your config file:

```json
{
  "instruments": [
    {"name": "BTCUSDT", "source": "binance", "timeframes": ["1m", "5s", "15m", "1h"]},
    {"name": "ETHUSDT", "source": "binance", "timeframes": ["5s", "10s", "5m"]}
  ],
  "dataBases": [
    {
      "type": "clickhouse",
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
| `ps.timeframe.{SYMBOL}@{SOURCE}` | Timeframes for instrument (e.g., `1m,5m,1h`) | - |
| `ps.buffer.size` | Disruptor ring buffer size (power of 2) | 4096 |
| `ps.http.port` | HTTP server port | 8080 |
| `ps.clickhouse.url` | ClickHouse JDBC URL | `jdbc:clickhouse://localhost:8123` |
| `ps.clickhouse.user` | ClickHouse username | `default` |
| `ps.clickhouse.password` | ClickHouse password | `` |
| `ps.repository.type` | Repository class name | `com.price.db.ClickHouseRepository` |

### Timeframe Format

Timeframes use format `<number><unit>`:
- `s` - seconds (e.g., `5s`, `30s`)
- `m` - minutes (e.g., `1m`, `5m`, `15m`)
- `h` - hours (e.g., `1h`, `4h`)
- `d` - days (e.g., `1d`)

## Storage Layer

### ClickHouse Schema

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

## Development

### Build Commands

```bash
# Build all modules
./gradlew build

# Build specific module
./gradlew :price-stream:build
./gradlew :price-query:build

# Run tests
./gradlew test

# Create fat JAR
./gradlew :price-stream:fatJar
./gradlew :price-query:bootJar

# Clean
./gradlew clean
```

### Local Development

1. **Start ClickHouse**
   ```bash
   docker compose up clickhouse
   ```

2. **Run Stream Server**
   ```bash
   CONFIG_FILE=config/config.json ./gradlew :price-stream:run
   ```

3. **Run Query Server**
   ```bash
   CONFIG_FILE=config/config.json ./gradlew :price-query:bootRun
   ```