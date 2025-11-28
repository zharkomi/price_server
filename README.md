# price_server

A high-performance Java 21 market data aggregation server that collects real-time cryptocurrency price data from exchanges and aggregates it into OHLCV (Open, High, Low, Close, Volume) candles at configurable timeframes.

## Description

This server connects to cryptocurrency exchanges via WebSocket to receive real-time market data, then aggregates tick-level price updates into time-based candles (1m, 5m, 1h, etc.). The system is designed for low-latency event processing using the LMAX Disruptor framework for lock-free concurrent data structures.

### Key Features

- **Real-time data collection** from cryptocurrency exchanges (currently supports Binance)
- **Multi-timeframe aggregation** - configure multiple timeframes per instrument (e.g., 1m, 5m, 15m, 1h candles)
- **High-performance architecture** using LMAX Disruptor for lock-free event processing
- **Non-drifting timer** ensures candles are emitted at exact intervals even during low-volume periods
- **Environment-based configuration** - no configuration files, all settings via environment variables
- **Multiple instrument support** - monitor multiple trading pairs simultaneously

### Architecture

The system uses a producer-consumer pattern where:
- WebSocket connectors receive market data and publish events
- MarketDataProcessor instances use Disruptor ring buffers for each instrument
- CandleAggregator handlers aggregate data into OHLCV candles for each timeframe
- CandleProcessor handles completed candles (storage/distribution)

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

## How to Start

### Prerequisites

- Java 21 or higher
- Internet connection (for WebSocket connection to exchanges)

### Build and Run

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd price_server
   ```

2. **Configure environment variables**
   ```bash
   # Set up your instruments and timeframes
   export ps.instruments="BTCUSDT@BINANCE"
   export ps.timeframe.BTCUSDT@BINANCE="1m,5m,15m"
   ```

3. **Build the project**
   ```bash
   ./gradlew build
   ```

4. **Run the server**
   ```bash
   ./gradlew run
   ```

### Alternative: Run with inline configuration

```bash
ps.instruments="BTCUSDT@BINANCE,ETHUSDT@BINANCE" \
ps.timeframe.BTCUSDT@BINANCE="1m,5m,1h" \
ps.timeframe.ETHUSDT@BINANCE="1m,5m" \
./gradlew run
```

### Verify Operation

The server will log its startup sequence and market data processing. Look for:
- "Starting market data server..." at startup
- WebSocket connection messages for each exchange
- Market data events being processed (if logging level is set appropriately)

### Shutdown

Press `Ctrl+C` to gracefully shutdown the server. The shutdown hook will:
- Close all WebSocket connections
- Flush remaining candles
- Stop all processors and timers

## Development

### Build Tasks

```bash
# Clean build artifacts
./gradlew clean

# Compile without running
./gradlew build

# Run tests (when available)
./gradlew test

# View all available tasks
./gradlew tasks
```

### Log Configuration

Logging is configured via `src/main/resources/log4j2.xml`. By default:
- Console output at INFO level
- Rolling file logs in `logs/` directory
- Separate rolling file for errors

## Project Structure

```
src/main/java/com/price/
├── Server.java              # Main entry point
├── Configuration.java       # Environment variable parser
├── connector/
│   ├── Connector.java       # Interface for exchange connectors
│   ├── ConnectorFactory.java
│   └── BinanceConnector.java
├── processor/
│   ├── MarketDataProcessor.java  # Disruptor-based event processor
│   ├── CandleAggregator.java     # OHLCV aggregation logic
│   └── CandleProcessor.java      # Candle storage/distribution
├── timer/
│   └── NonDriftingTimer.java     # Precise interval timer
└── event/
    └── MarketDataEvent.java      # Event types (DATA, TIMER)
```

## Contributing

For detailed architecture and development guidelines, see `CLAUDE.md`.
