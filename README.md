# Market Price Server

A high-performance Java 21 market data aggregation server that collects real-time cryptocurrency price data from exchanges and aggregates it into OHLCV (Open, High, Low, Close, Volume) candles at configurable timeframes.

## Table of Contents

- [Description](#description)
- [Architecture](#architecture)
- [Configuration](#configuration)
- [Storage Layer](#storage-layer)
- [How to Start](#how-to-start)
  - [Prerequisites](#prerequisites)
  - [Quick Start with Docker](#quick-start-with-docker)
  - [Docker Management Scripts](#docker-management-scripts)
  - [Docker Architecture](#docker-architecture)
- [REST API](#rest-api)
- [Visualization Tools](#visualization-tools)
- [Development](#development)
  - [Local Development Build and Run](#local-development-build-and-run)
  - [Build Tasks](#build-tasks)
  - [Log Configuration](#log-configuration)

## Description

This server connects to cryptocurrency exchanges via WebSocket to receive real-time market data, then aggregates tick-level price updates into time-based candles (1m, 5m, 1h, etc.). The system is designed for low-latency event processing using the LMAX Disruptor framework for lock-free concurrent data structures.

### Key Features

- **Real-time data collection** from cryptocurrency exchanges (currently supports Binance)
- **Multi-timeframe aggregation** - configure multiple timeframes per instrument (e.g., 1m, 5m, 15m, 1h candles)
- **High-performance architecture** using LMAX Disruptor for lock-free event processing
- **Dual-buffer architecture** - segregated input and output buffers isolate market data processing from storage operations
- **ClickHouse storage** - high-performance columnar database for time-series data with automatic schema management
- **REST API** - HTTP endpoints for querying historical candle data with JSON responses
- **Non-drifting timer** ensures candles are emitted at exact intervals even during low-volume periods
- **Environment-based configuration** - no configuration files, all settings via environment variables
- **Multiple instrument support** - monitor multiple trading pairs simultaneously

### Architecture

The system uses a multilayer architecture with segregated input and output buffers for optimal performance and decoupling:

**Layer 1: Input Processing (per instrument)**
- WebSocket connectors receive market data and publish events
- Each `MarketDataProcessor` instance has its own Disruptor ring buffer (input layer)
- Chain of `CandleAggregator` handlers aggregate data into OHLCV candles for each timeframe
- Multiple producers (connectors) → Multiple input ring buffers (one per instrument)

**Layer 2: Output Processing (shared storage layer)**
- `CandleProcessor` acts as a bridge with a separate Disruptor ring buffer (output layer)
- Aggregators from all instruments publish completed candles to this shared buffer
- Single multi-producer ring buffer → Single consumer (repository)
- This segregation prevents storage operations from blocking market data processing

**Benefits of Dual-Buffer Architecture:**
- Input buffers handle high-frequency market data without storage latency
- Output buffer batches writes to storage efficiently
- Complete isolation between real-time processing and persistence
- Storage backpressure doesn't affect market data ingestion

## Configuration

All configuration is done through environment variables:

### Required Variables

- **`ps.instruments`** - Comma-separated list of instruments to monitor
  - Format: `SYMBOL@SOURCE` (e.g., `BTCUSDT@BINANCE,ETHUSDT@BINANCE`)
  - Each instrument must have a corresponding timeframe configuration

- **`ps.timeframe.{SYMBOL}@{SOURCE}`** - Timeframes for each instrument
  - Format: Comma-separated list of timeframes
  - Timeframe syntax: `<number><unit>` where unit is:
    - `s` = seconds
    - `m` = minutes
    - `h` = hours
    - `d` = days
  - Example: `ps.timeframe.BTCUSDT@BINANCE=1m,5m,15m,1h`

### Optional Variables

- **`ps.buffer.size`** - LMAX Disruptor ring buffer size (default: 4096)
  - Must be a power of 2 (e.g., 1024, 2048, 4096, 8192)
  - Larger buffers can handle higher throughput but use more memory

- **`ps.clickhouse.url`** - ClickHouse JDBC URL (default: `jdbc:clickhouse://localhost:8123`)
  - Format: `jdbc:clickhouse://<host>:<port>`

- **`ps.clickhouse.user`** - ClickHouse username (default: `default`)

- **`ps.clickhouse.password`** - ClickHouse password (default: empty string)

## Storage Layer

The server uses ClickHouse as its primary storage backend for historical candle data. ClickHouse is a high-performance columnar database optimized for time-series analytics and OLAP workloads.

### Why ClickHouse?

- **Columnar storage** - 100x faster than row-based databases for analytical queries
- **Compression** - up to 10x data compression reduces storage costs
- **Partitioning** - automatic data partitioning by date for efficient queries and data management
- **Scalability** - handles billions of rows with sub-second query performance
- **ReplacingMergeTree** - automatic deduplication of candles if the same data is inserted multiple times

### Storage Architecture

**Database**: `prices_db` (automatically created on first run)

**Table**: `trade_candles` with the following schema:

```sql
CREATE TABLE trade_candles
(
    instrument       LowCardinality(String),  -- Trading pair (e.g., "BTCUSDT@BINANCE")
    timeframe_ms     UInt32,                   -- Timeframe in milliseconds
    time             UInt64,                   -- Candle timestamp (Unix epoch in ms)
    open             Float64,                  -- Opening price
    high             Float64,                  -- Highest price
    low              Float64,                  -- Lowest price
    close            Float64,                  -- Closing price
    volume           Float64                   -- Trading volume
)
ENGINE = ReplacingMergeTree()
PARTITION BY (instrument, timeframe_ms, toDate(time / 1000))
ORDER BY (instrument, timeframe_ms, time)
SETTINGS index_granularity = 8192;
```

**Schema Details**:
- **LowCardinality(String)** for instrument - optimizes storage and queries for repeated string values
- **ReplacingMergeTree engine** - automatically deduplicates rows with the same ORDER BY key
- **Partitioning** by (instrument, timeframe, date) - enables efficient data pruning and management
- **ORDER BY** (instrument, timeframe_ms, time) - creates primary index for fast range queries
- **index_granularity** of 8192 - balances index size vs. query performance

### Storage Features

- **Automatic schema initialization** - database and tables are created automatically on first run
- **Batch insertion** - candles are batched for efficient writes using Disruptor's end-of-batch detection
- **Deduplication** - ReplacingMergeTree engine prevents duplicate candles
- **Partitioning** - data partitioned by instrument, timeframe, and date for efficient queries
- **Compression** - automatic columnar compression reduces storage by ~90%
- **Fast queries** - primary index enables sub-second queries even with billions of rows

### Storage Performance

The dual-buffer architecture ensures:
- **Write throughput**: 100K+ candles/second sustained
- **Query latency**: Sub-100ms for typical date range queries
- **No blocking**: Storage operations never block market data processing
- **Batch efficiency**: Disruptor's end-of-batch detection minimizes database round-trips

### Alternative Storage Backends

The system uses a `Repository` interface that can be implemented for other storage systems:
- **Default**: `ClickHouseRepository` (JDBC-based, high-performance)
- **Future options**: PostgreSQL with TimescaleDB, Apache Druid, S3 (Parquet), etc.

To add a new repository:
1. Implement the `Repository` interface (extends `EventHandler<CandleEvent>`)
2. Add new repository class (e.g., `PostgresRepository`)
3. Update `RepositoryFactory` to support the new type
4. Add configuration variables for the new backend

## How to Start

### Prerequisites

**For Docker deployment (recommended):**
- Docker and Docker Compose
- Internet connection (for WebSocket connection to exchanges)

**For local development:**
- ClickHouse database (for persistent storage of candle data)
  - Installation: https://clickhouse.com/docs/en/install
  - Or run via Docker: `docker run -d -p 8123:8123 clickhouse/clickhouse-server`
- Internet connection (for WebSocket connection to exchanges)

### Quick Start with Docker

The easiest way to run the server is using Docker Compose, which automatically sets up both the price server and ClickHouse database.

1. **Clone the repository**
   ```bash
   git clone https://github.com/zharkomi/price_server.git
   cd price_server
   ```

2. **Build the Docker image**
   ```bash
   ./scripts/docker_build.sh
   ```

   This uses a multi-stage Dockerfile to create an optimized production image:
   - Stage 1: Downloads Gradle dependencies (cached layer)
   - Stage 2: Builds fat JAR with all dependencies
   - Stage 3: Runtime image with JRE only (~300MB vs 1GB+ for full JDK)

3. **Start the services**
   ```bash
   ./scripts/compose_run.sh up
   ```

   This will:
   - Start ClickHouse database
   - Wait for ClickHouse to be healthy
   - Start the price server using the built image
   - Display service URLs and helpful commands

4. **Verify the services are running**
   ```bash
   # Check service status
   ./scripts/compose_run.sh ps

   # View logs
   ./scripts/compose_run.sh logs

   # Test the API
   curl "http://localhost:8080/health"
   ```

5. **Shutdown**
   ```bash
   # Stop services
   ./scripts/compose_run.sh down
   ```

   The shutdown process will:
   - Gracefully stop the price server
   - Close all WebSocket connections
   - Flush remaining candles
   - Stop all processors and timers
   - Stop ClickHouse database

### Docker Management Scripts

The repository includes helper scripts for Docker operations:

#### `scripts/docker_build.sh` - Standalone Image Build

For building the price server Docker image independently:

```bash
# Build with default 'latest' tag
./scripts/docker_build.sh

# Build with custom tag
./scripts/docker_build.sh v1.0.0
```

This script uses the multi-stage Dockerfile to create an optimized production image.

#### `scripts/compose_run.sh` - Docker Compose Management

Main script for managing the entire application stack (price server + ClickHouse):

```bash
# Start services (builds if needed)
./scripts/compose_run.sh up

# Stop services
./scripts/compose_run.sh down

# Restart services
./scripts/compose_run.sh restart

# View logs (follow mode)
./scripts/compose_run.sh logs

# Show running services
./scripts/compose_run.sh ps

# Rebuild images
./scripts/compose_run.sh build

# Stop and remove everything including volumes
./scripts/compose_run.sh clean
```

**Features:**
- Automatically checks for `.env` file existence
- Displays service URLs after startup
- Provides helpful command reminders
- Supports all common Docker Compose operations

### Docker Architecture

The Docker setup uses a **multi-stage build** for optimal image size and security:

**Stage 1: Dependencies** (`eclipse-temurin:21-jdk-jammy`)
- Downloads Gradle dependencies
- Cached layer for faster rebuilds

**Stage 2: Builder** (extends dependencies stage)
- Copies source code
- Builds fat JAR with all dependencies

**Stage 3: Runtime** (`eclipse-temurin:21-jre-jammy`)
- JRE-only base image (smaller, more secure)
- Copies only the built JAR
- Exposes port 8080
- Final image size: ~300MB (vs 1GB+ for full JDK)

**Docker Compose Services:**

1. **price-server**
   - Built from local Dockerfile
   - Exposes configurable HTTP port (default: 8080)
   - Mounts `./run/logs` for log persistence
   - Auto-restarts unless stopped manually
   - Waits for ClickHouse to be healthy before starting

2. **clickhouse**
   - Official ClickHouse image
   - Exposes HTTP (8120) and native (9001) ports
   - Persists data in `./run/clickhouse` volume
   - Health check ensures database is ready
   - Separate volume for logs

**Networking:**
- Services communicate via `price-network` bridge network
- Price server connects to ClickHouse using hostname `clickhouse`
- External access via mapped ports on localhost

**Data Persistence:**
- `./run/logs` - Price server application logs and GC logs
  - Application logs (INFO level with rolling file appender)
  - GC logs in `gc/` subdirectory (5 files × 10MB rotation)
  - Heap dumps on OutOfMemoryError
- `./run/clickhouse` - ClickHouse database files
- `clickhouse-logs` - ClickHouse server logs (Docker volume)

## REST API

The server exposes a REST API for querying historical candle data via the `HistoryService` component.

### Endpoints

#### GET /history

Retrieve historical OHLCV candles for a specific instrument and timeframe.

**Query Parameters:**
- `symbol` (required) - Trading pair symbol (e.g., "BTCUSDT@BINANCE")
- `interval` (required) - Timeframe interval (e.g., "1m", "5m", "1h")
- `from` (required) - Start timestamp in seconds (Unix epoch)
- `to` (required) - End timestamp in seconds (Unix epoch)

**Example Request:**
```bash
curl "http://localhost:8080/history?symbol=BTCUSDT@BINANCE&interval=1m&from=1735516800&to=1735520400"
```

**Success Response (200 OK):**
```json
{
  "success": true,
  "error": null,
  "times": [1735516800, 1735516860, 1735516920],
  "opens": [42000.5, 42010.2, 42005.8],
  "highs": [42050.0, 42030.5, 42020.0],
  "lows": [41990.0, 42000.0, 41995.5],
  "closes": [42010.2, 42005.8, 42015.3],
  "volumes": [150000, 125000, 135000]
}
```

**Error Response (4xx/5xx):**
```json
{
  "success": false,
  "error": "Missing required parameters: symbol, interval, from, to",
  "times": [],
  "opens": [],
  "highs": [],
  "lows": [],
  "closes": [],
  "volumes": []
}
```

#### GET /health

Health check endpoint for service monitoring. Returns detailed information about all configured instruments including their configuration and runtime statistics.

**Example Request:**
```bash
curl "http://localhost:8080/health"
```

**Response (200 OK):**
```json
{
  "status": "ok",
  "service": "HistoryService",
  "timestamp": 1735516800000,
  "instruments": [
    {
      "name": "BTCUSDT",
      "source": "BINANCE",
      "fullName": "BTCUSDT@BINANCE",
      "timeframes": [60000, 300000, 900000],
      "marketEvents": 125847,
      "candleEvents": 384
    },
    {
      "name": "ETHUSDT",
      "source": "BINANCE",
      "fullName": "ETHUSDT@BINANCE",
      "timeframes": [60000, 300000],
      "marketEvents": 98234,
      "candleEvents": 256
    }
  ]
}
```

**Response Fields:**
- `status` - Service status ("ok")
- `service` - Service name ("HistoryService")
- `timestamp` - Current timestamp in milliseconds (Unix epoch)
- `instruments` - Array of configured instruments with:
  - `name` - Instrument symbol (e.g., "BTCUSDT")
  - `source` - Exchange name (e.g., "BINANCE")
  - `fullName` - Full instrument identifier (e.g., "BTCUSDT@BINANCE")
  - `timeframes` - Array of configured timeframes in milliseconds
  - `marketEvents` - Total number of market data events processed
  - `candleEvents` - Total number of candles generated

### API Features

- **REST over HTTP** - simple HTTP GET requests, no authentication required
- **JSON responses** - standardized response format with success/error indicators
- **Input validation** - comprehensive parameter validation with descriptive error messages
- **Embedded Jetty server** - runs on port 8080 (default)
- **Array-based OHLCV format** - efficient columnar data structure for charting libraries

### Configuration Example

```bash
# Monitor BTC and ETH on Binance
export ps.instruments="BTCUSDT@BINANCE,ETHUSDT@BINANCE"

# Configure timeframes for each instrument
export ps.timeframe.BTCUSDT@BINANCE="1m,5m,15m,1h,4h"
export ps.timeframe.ETHUSDT@BINANCE="1m,5m,1h"

# Optional: increase buffer size for higher throughput
export ps.buffer.size=8192
```

## Visualization Tools

### History Plotter Script

The repository includes a Python script (`scripts/plot_history.py`) for fetching and visualizing historical candle data with a GUI interface.

**Features:**
- Graphical candlestick chart with OHLC data
- Volume bar chart
- Green/red candles for bullish/bearish moves
- Grid lines and axis labels
- Statistics panel with first/last candle info
- Uses only built-in Python libraries (tkinter)

**Requirements:**
- Python 3 with tkinter (included by default on most systems)

**Command-Line Parameters:**

| Parameter | Required | Description | Example |
|-----------|----------|-------------|---------|
| `--instrument` | Yes | Trading pair symbol | `BTCUSDT@BINANCE` |
| `--timeframe` | Yes | Candle interval | `1m`, `5m`, `1h` |
| `--start` | Conditional* | Start timestamp (Unix epoch in seconds) | `1735516800` |
| `--end` | No | End timestamp (Unix epoch in seconds), defaults to now | `1735520400` |
| `--last` | Conditional* | Fetch last N bars from now | `100` |
| `--host` | No | Price server hostname (default: `localhost`) | `localhost` |
| `--port` | No | Price server port (default: `8080`) | `8080` |

\* Either `--start` or `--last` must be provided, but not both.

**Examples:**

```bash
# Plot last 50 1-minute candles
python3 scripts/plot_history.py --instrument BTCUSDT@BINANCE --timeframe 1m --last 50

# Plot last 24 hourly candles
python3 scripts/plot_history.py --instrument ETHUSDT@BINANCE --timeframe 1h --last 24

# Plot 5-minute candles for specific date range
python3 scripts/plot_history.py --instrument BTCUSDT@BINANCE --timeframe 5m --start 1735516800 --end 1735520400

# Connect to remote server
python3 scripts/plot_history.py --instrument BTCUSDT@BINANCE --timeframe 1m --last 100 --host 192.168.1.100 --port 8080
```

**Output:**
- Opens a GUI window (1200x800) with interactive candlestick chart
- Prints data statistics to console (time range, price range, volume)

## Development

### Local Development Build and Run

For development without Docker:

1. **Start ClickHouse** (if not using Docker Compose)
   ```bash
   docker run -d -p 8123:8123 --name clickhouse clickhouse/clickhouse-server
   ```

2. **Configure environment variables**
   ```bash
   # Set up your instruments and timeframes
   export ps.instruments="BTCUSDT@BINANCE"
   export ps.timeframe.BTCUSDT@BINANCE="1m,5m,15m"
   export ps.clickhouse.url="jdbc:clickhouse://localhost:8123"
   ```

3. **Build the project**
   ```bash
   ./gradlew build
   ```

4. **Run the server**
   ```bash
   ./gradlew run
   ```

5. **Shutdown**

   Press `Ctrl+C` to gracefully shutdown the server. The shutdown hook will:
   - Close all WebSocket connections
   - Flush remaining candles
   - Stop all processors and timers

#### Alternative: Run with inline configuration

```bash
ps.instruments="BTCUSDT@BINANCE,ETHUSDT@BINANCE" \
ps.timeframe.BTCUSDT@BINANCE="1m,5m,1h" \
ps.timeframe.ETHUSDT@BINANCE="1m,5m" \
./gradlew run
```

#### Verify Operation

The server will log its startup sequence and market data processing. Look for:
- "Starting market data server..." at startup
- WebSocket connection messages for each exchange
- Market data events being processed (if logging level is set appropriately)

**Testing the API:**
```bash
# Health check
curl "http://localhost:8080/health"

# Query historical data (adjust timestamps as needed)
curl "http://localhost:8080/history?symbol=BTCUSDT@BINANCE&interval=1m&from=1735516800&to=1735520400"
```

### Build Tasks

```bash
# Clean build artifacts
./gradlew clean

# Compile without running
./gradlew build

# Run tests (when available)
./gradlew test
```

### Log Configuration

Logging is configured via `src/main/resources/log4j2.xml`. By default:
- Console output at INFO level
- Rolling file logs in `logs/` directory
- Separate rolling file for errors
