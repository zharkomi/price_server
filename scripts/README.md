# Scripts

Python utility scripts for testing and visualizing price server data.

## Scripts

### ws_client.py

WebSocket client for testing the StreamService real-time candle subscriptions.

**Usage:**
```bash
python scripts/ws_client.py [host] [port]
```

**Default:** `localhost:8081`

**Example:**
```bash
# Connect to local server
python scripts/ws_client.py

# Connect to remote server
python scripts/ws_client.py 192.168.1.100 8081
```

**Behavior:**
- Connects to WebSocket endpoint `ws://{host}:{port}/stream`
- Subscribes to sample instruments (BTCUSDT@BINANCE 5s, ETHUSDT@BINANCE 5s/10s)
- Prints all received messages with timestamps
- Press `Ctrl+C` to exit

**Sample Output:**
```
[2024-01-15 10:30:00.123] >>> SENDING:
{
  "type": "SUBSCRIBE",
  "instrument": "BTCUSDT@BINANCE",
  "timeframe": 5000
}

[2024-01-15 10:30:05.001] <<< RECEIVED:
[
  {
    "i": "BTCUSDT@BINANCE",
    "t": 1705315800000,
    "f": 5000,
    "o": 42150.5,
    "h": 42155.0,
    "l": 42148.2,
    "c": 42152.3,
    "v": 12500
  }
]
```

---

### plot_history.py

Fetches historical OHLCV data from the REST API and displays an interactive candlestick chart using tkinter.

**Requirements:**
- Python 3 with tkinter (included by default)

**Usage:**
```bash
python scripts/plot_history.py --instrument SYMBOL --timeframe INTERVAL [options]
```

**Parameters:**

| Parameter | Required | Description | Example |
|-----------|----------|-------------|---------|
| `--instrument` | Yes | Trading pair symbol | `BTCUSDT@BINANCE` |
| `--timeframe` | Yes | Candle interval | `1m`, `5m`, `1h` |
| `--start` | Conditional* | Start timestamp (Unix seconds) | `1735516800` |
| `--end` | No | End timestamp (defaults to now) | `1735520400` |
| `--last` | Conditional* | Fetch last N bars | `100` |
| `--host` | No | Server hostname | `localhost` |
| `--port` | No | Server port | `8080` |

\* Either `--start` or `--last` must be provided.

**Examples:**

```bash
# Plot last 50 1-minute candles
python scripts/plot_history.py --instrument BTCUSDT@BINANCE --timeframe 1m --last 50

# Plot last 24 hourly candles
python scripts/plot_history.py --instrument ETHUSDT@BINANCE --timeframe 1h --last 24

# Plot specific time range
python scripts/plot_history.py --instrument BTCUSDT@BINANCE --timeframe 5m \
    --start 1735516800 --end 1735520400

# Connect to remote server
python scripts/plot_history.py --instrument BTCUSDT@BINANCE --timeframe 1m \
    --last 100 --host 192.168.1.100 --port 8080
```

**Features:**
- Candlestick chart with OHLC data (green/red candles)
- Volume bar chart below price chart
- Grid lines and axis labels
- Statistics panel (first/last candle, total bars)
- Console output with data statistics

**GUI Window:**
- Size: 1200x800 pixels
- Top section: Candlestick price chart
- Bottom section: Volume bars
- Footer: Statistics panel
