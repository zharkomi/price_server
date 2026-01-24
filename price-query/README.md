# Price Query

REST API service for querying historical OHLCV candle data from ClickHouse. Built with Spring Boot 3.2.

## Features

- REST API endpoint for historical candle queries
- Spring Boot with HikariCP connection pooling
- Compact JSON response format optimized for charting libraries

## REST API

### GET /history

Query historical OHLCV candles.

**Parameters:**

| Parameter | Required | Description | Example |
|-----------|----------|-------------|---------|
| `symbol` | Yes | Instrument identifier | `BTCUSDT@BINANCE` |
| `interval` | Yes | Timeframe | `1m`, `5m`, `1h` |
| `from` | Yes | Start timestamp (Unix seconds) | `1735516800` |
| `to` | Yes | End timestamp (Unix seconds) | `1735520400` |

**Example Request:**

```bash
curl "http://localhost:8080/history?symbol=BTCUSDT@BINANCE&interval=1m&from=1735516800&to=1735520400"
```

**Success Response (200 OK):**

```json
{
  "s": "ok",
  "errmsg": null,
  "t": [1735516800, 1735516860, 1735516920],
  "o": [42000.5, 42010.2, 42005.8],
  "h": [42050.0, 42030.5, 42020.0],
  "l": [41990.0, 42000.0, 41995.5],
  "c": [42010.2, 42005.8, 42015.3],
  "v": [150000, 125000, 135000]
}
```

| Field | Description |
|-------|-------------|
| `s` | Status (`ok` or `error`) |
| `errmsg` | Error message (null on success) |
| `t` | Array of timestamps (seconds) |
| `o` | Array of open prices |
| `h` | Array of high prices |
| `l` | Array of low prices |
| `c` | Array of close prices |
| `v` | Array of volumes |

**Error Response:**

```json
{
  "s": "error",
  "errmsg": "Missing required parameter: symbol",
  "t": [],
  "o": [],
  "h": [],
  "l": [],
  "c": [],
  "v": []
}
```

## Package Structure

```
com.price.query
├── QueryServer.java              # Spring Boot entry point
├── ApplicationContext.java       # Configuration beans
├── controller
│   └── HistoryController.java    # REST endpoint
├── service
│   └── HistoryService.java       # Business logic
└── dto
    └── HistoryResponse.java      # Response DTO

com.price.db
└── ClickHouseRepository.java     # QueryRepository implementation
```

## Components

| Component | Description |
|-----------|-------------|
| `QueryServer` | Spring Boot application entry point |
| `ApplicationContext` | Configures `PriceConfiguration`, `RepositoryFactory`, and `QueryRepository` beans |
| `HistoryController` | REST controller exposing `/history` endpoint |
| `HistoryService` | Parses timeframes, queries repository, formats response |
| `HistoryResponse` | DTO with compact field names for JSON serialization |
| `ClickHouseRepository` | HikariCP-based ClickHouse query implementation |

## Testing

```bash
./gradlew :price-query:test
```

Tests use MockMvc for controller testing with mocked services.
