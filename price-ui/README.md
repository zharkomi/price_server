# Price UI

Angular web application for visualizing real-time price data using TradingView's Lightweight Charts.

## Features

- Full-window candlestick chart with TradingView's lightweight-charts library
- Exchange, instrument, and timeframe selectors
- Configurable history preload (number of bars)
- Real-time WebSocket updates
- Start/Stop streaming control

## Prerequisites

- Node.js 18+
- npm 9+

## Development

### Install dependencies

```bash
cd price-ui
npm install
```

### Run development server

```bash
npm start
```

The app will be available at `http://localhost:4200`.

The development server proxies API requests:
- `/api/*` → `http://localhost:8080/*` (REST API)
- `/ws/*` → `ws://localhost:8081/*` (WebSocket)

### Build for production

```bash
npm run build
```

Output will be in `dist/price-ui/`.

## Architecture

```
src/
├── main.ts                 # Bootstrap
├── index.html              # HTML template
├── styles.css              # Global styles
└── app/
    ├── app.component.ts    # Main component with chart
    ├── app.component.html  # Template with controls
    ├── app.component.css   # Component styles
    ├── models/
    │   └── config.model.ts # TypeScript interfaces
    └── services/
        ├── config.service.ts   # GET /config
        ├── history.service.ts  # GET /history
        └── stream.service.ts   # WebSocket client
```

## API Integration

### Configuration Endpoint

```
GET /api/config
```

Returns available instruments and timeframes:

```json
{
  "status": "ok",
  "service": "PriceQueryService",
  "instruments": [
    {"name": "BTCUSDT", "source": "binance", "timeframes": ["1m", "5s", "15m", "1h"]}
  ],
  "timestamp": 1735516800000
}
```

### History Endpoint

```
GET /api/history?symbol=BTCUSDT@BINANCE&interval=1m&from=1735516800&to=1735520400
```

Returns OHLCV data arrays:

```json
{
  "s": "ok",
  "t": [1735516800, 1735516860],
  "o": [42000.5, 42010.2],
  "h": [42050.0, 42030.5],
  "l": [41990.0, 42000.0],
  "c": [42010.2, 42005.8],
  "v": [150000, 125000]
}
```

### WebSocket Streaming

Connect to `ws://host:8081/stream`

Subscribe message:
```json
{"type": "SUBSCRIBE", "instrument": "BTCUSDT@BINANCE", "timeframe": 60000}
```

Receive candle updates:
```json
[{"i": "BTCUSDT@BINANCE", "t": 1735516800000, "f": 60000, "o": 42000.5, "h": 42050.0, "l": 41990.0, "c": 42010.2, "v": 150000}]
```
