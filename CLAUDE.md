# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Java 21 market data aggregation server that collects real-time price data from cryptocurrency exchanges (currently Binance) and aggregates it into OHLCV (Open, High, Low, Close, Volume) candles at configurable timeframes. The system uses high-performance concurrent data structures (LMAX Disruptor) for low-latency event processing.

## Build and Run Commands

```bash
# Build the project
./gradlew build

# Run the application (requires environment variables - see Configuration)
./gradlew run

# Clean build artifacts
./gradlew clean
```

## Configuration

The application is configured entirely via environment variables:

- `ps.instruments` - Comma-separated list of instruments in format `NAME@SOURCE` (e.g., `BTCUSDT@BINANCE,ETHUSDT@BINANCE`)
- `ps.timeframe.{NAME}@{SOURCE}` - Comma-separated timeframes for each instrument (e.g., `ps.timeframe.BTCUSDT@BINANCE=1m,5m,1h`)
  - Timeframe format: number + unit (s=seconds, m=minutes, h=hours, d=days)
- `ps.buffer.size` - LMAX Disruptor ring buffer size (default: 4096, must be power of 2)

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
WebSocket (Binance) → Connector → MarketDataProcessor → Disruptor → CandleAggregator(s) → CandleProcessor → Storage
                                           ↑
                                    NonDriftingTimer
```

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
- Currently empty placeholder
- Intended for storage/distribution of completed candles

### Concurrency Model

- **Single producer, multi-consumer**: One WebSocket thread per source produces events, multiple `CandleAggregator` handlers (one per timeframe) consume them sequentially
- **LMAX Disruptor**: Each `MarketDataProcessor` has its own ring buffer for lock-free event passing
- **Event handler chain**: CandleAggregators are chained using `.then()`, ensuring sequential processing per timeframe (1m processes before 5m before 1h)

### Adding New Exchange Support

1. Add new value to `Source` enum with `createConnector()` implementation
2. Create new connector class implementing `Connector` interface
3. Implement `register()`, `start()`, and `close()` methods
4. Convert exchange-specific data format to `MarketDataEvent`

## Important Notes

- The mainClass in build.gradle is `com.priceserver.app.Application` but actual main class is `com.price.Server` - this needs to be corrected
- Disruptor buffer size must be a power of 2
- Timer events are critical for candle emission during low-volume periods
- Each MarketDataProcessor processes events for a single instrument only
- CandleProcessor is currently a stub and needs implementation for persistence/distribution
