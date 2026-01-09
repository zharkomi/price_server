#!/usr/bin/env python3
"""
WebSocket client for price_server StreamService.
Connects to the WebSocket server and subscribes to candle streams.
"""

import asyncio
import json
import sys
from datetime import datetime

try:
    import websockets
except ImportError:
    print("Please install websockets: pip install websockets")
    sys.exit(1)


def timestamp() -> str:
    """Return current timestamp in readable format."""
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S.%f")[:-3]


def format_json(data: dict | list) -> str:
    """Format JSON with indentation."""
    return json.dumps(data, indent=2)


async def send_message(ws, message: dict) -> None:
    """Send a message and print it with timestamp."""
    json_str = json.dumps(message)
    print(f"\n[{timestamp()}] >>> SENDING:")
    print(format_json(message))
    await ws.send(json_str)


async def subscribe(ws, instrument: str, timeframe_ms: int) -> None:
    """Subscribe to an instrument stream."""
    message = {
        "type": "SUBSCRIBE",
        "instrument": instrument,
        "timeframe": timeframe_ms
    }
    await send_message(ws, message)


async def main():
    host = sys.argv[1] if len(sys.argv) > 1 else "localhost"
    port = int(sys.argv[2]) if len(sys.argv) > 2 else 8081
    uri = f"ws://{host}:{port}/stream"

    print(f"[{timestamp()}] Connecting to {uri}...")

    try:
        async with websockets.connect(uri) as ws:
            print(f"[{timestamp()}] Connected!")

            # Subscribe to BTCUSDT 5s (5000ms) and ETHUSDT 10s (10000ms)
            await subscribe(ws, "BTCUSDT@BINANCE", 5000)
            await subscribe(ws, "ETHUSDT@BINANCE", 5000)
            await subscribe(ws, "ETHUSDT@BINANCE", 10000)

            print(f"\n[{timestamp()}] Listening for messages (Ctrl+C to exit)...")
            print("-" * 60)

            # Listen for incoming messages
            async for message in ws:
                try:
                    data = json.loads(message)
                    print(f"\n[{timestamp()}] <<< RECEIVED:")
                    print(format_json(data))
                except json.JSONDecodeError:
                    print(f"\n[{timestamp()}] <<< RECEIVED (raw):")
                    print(message)

    except websockets.exceptions.ConnectionClosed as e:
        print(f"\n[{timestamp()}] Connection closed: {e}")
    except ConnectionRefusedError:
        print(f"\n[{timestamp()}] Connection refused. Is the server running on {uri}?")
    except KeyboardInterrupt:
        print(f"\n[{timestamp()}] Interrupted by user")


if __name__ == "__main__":
    print("Price Server WebSocket Client")
    print("=" * 60)
    print("Usage: python ws_client.py [host] [port]")
    print("Default: localhost:8081")
    print("=" * 60)
    asyncio.run(main())