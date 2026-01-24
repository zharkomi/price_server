# Price Stream

Real-time market data streaming server that collects price data from cryptocurrency exchanges, aggregates it into OHLCV candles, persists to storage, and distributes to WebSocket clients.

## Features

- Real-time WebSocket connection to Binance exchange
- Multi-timeframe OHLCV candle aggregation using LMAX Disruptor
- Dual-buffer architecture for optimal performance
- WebSocket streaming API for real-time subscriptions
- ClickHouse persistence with batch insertion

## Architecture

### Dual-Buffer

The system uses two segregated LMAX Disruptor ring buffers:

**Input Layer (per instrument)**
- One `MarketDataProcessor` per instrument with its own Disruptor
- Single producer (WebSocket connector) → chain of consumers (aggregators)
- Handles high-frequency market data without storage latency
- `YieldingWaitStrategy` for low-latency processing

**Output Layer (shared)**
- One `CandlePersistenceProcessor` per repository (multi-producer)
- All `CandleAggregator` instances publish completed candles here
- Single consumer (`ClickHouseRepository`) for batch writes
- Uses `isEndOfBatch()` detection for efficient database operations

**Benefits:**
- Input buffers handle high-frequency market data without storage latency
- Output buffer batches writes to storage efficiently
- Complete isolation between real-time processing and persistence
- Storage backpressure doesn't affect market data ingestion

### Components

| Component | Description |
|-----------|-------------|
| `StreamServer` | Entry point, orchestrates initialization and shutdown |
| `MarketDataProcessor` | Per-instrument Disruptor with aggregator chain |
| `CandleAggregator` | OHLCV aggregation logic, detects candle boundaries |
| `NonDriftingTimer` | Sends TIMER events at exact second boundaries |
| `ClientNotifier` | Routes events to subscribed WebSocket clients |
| `CandlePersistenceProcessor` | Output Disruptor bridge to storage |
| `ClickHouseRepository` | Batch insert to ClickHouse with auto-schema |
| `StreamService` | Netty WebSocket server for real-time subscriptions |
| `SubscriptionProcessor` | Per-client subscription management |
| `BinanceConnector` | WebSocket connection to Binance bookTicker stream |
| `ConnectorFactory` | Creates one connector per source (shared across instruments) |

## WebSocket Streaming API

Connect to `ws://host:8081/stream` (port = httpPort + 1)

### Subscribe

```json
{
  "type": "SUBSCRIBE",
  "instrument": "BTCUSDT@BINANCE",
  "timeframe": 60000
}
```

### Unsubscribe

```json
{
  "type": "UNSUBSCRIBE",
  "instrument": "BTCUSDT@BINANCE",
  "timeframe": 60000
}
```

### Receive Candle Events

Events are batched and sent on TIMER boundaries:

```json
[
  {
    "i": "BTCUSDT@BINANCE",
    "t": 1735516800000,
    "f": 60000,
    "o": 42000.5,
    "h": 42050.0,
    "l": 41990.0,
    "c": 42010.2,
    "v": 150000
  }
]
```

| Field | Description |
|-------|-------------|
| `i` | Instrument |
| `t` | Timestamp (ms) |
| `f` | Timeframe (ms) |
| `o` | Open |
| `h` | High |
| `l` | Low |
| `c` | Close |
| `v` | Volume |

## Package Structure

```
com.price.stream
├── StreamServer.java                    # Entry point
├── market
│   ├── MarketDataProcessor.java         # Per-instrument processor
│   ├── CandleAggregator.java            # OHLCV aggregation
│   ├── NonDriftingTimer.java            # Sync timer
│   ├── ClientNotifier.java              # Client event routing
│   ├── Connector.java                   # Connector interface
│   └── source
│       ├── BinanceConnector.java        # Binance WebSocket
│       └── ConnectorFactory.java        # Connector factory
├── storage
│   └── CandlePersistenceProcessor.java  # Output Disruptor
├── service
│   ├── StreamService.java               # Netty server
│   ├── ClientConnectionHandler.java     # WebSocket handler
│   └── SubscriptionProcessor.java       # Subscription manager
├── common
│   ├── CandleProcessor.java             # Candle consumer interface
│   └── SubscriptionKey.java             # Subscription identifier
└── event
    ├── buffer
    │   └── MarketDataEvent.java         # Internal Disruptor event
    └── client
        ├── InstrumentEvent.java         # Client candle event
        └── SubscriptionEvent.java       # Client subscription request

com.price.db
└── ClickHouseRepository.java            # Storage implementation
```

## Performance

- **Input throughput**: 100K+ market events/second per instrument
- **Output throughput**: 100K+ candles/second batch insert
- **Latency**: Sub-millisecond market data processing
- **No blocking**: Storage operations never block market data

## Adding New Exchange Support

1. Add value to `Source` enum in `price-common`
2. Create connector class implementing `Connector` interface:
   ```java
   public class NewExchangeConnector implements Connector {
       void register(MarketDataProcessor processor);
       void start();
       void close();
   }
   ```
3. Update `ConnectorFactory` to handle new source
4. Convert exchange data format to `MarketDataEvent`
