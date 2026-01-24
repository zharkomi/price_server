#!/usr/bin/env python3
"""
Market Price Server - History Plotter

Fetches historical OHLCV candle data from the price server and plots it using tkinter GUI.

Usage:
    # Fetch data for a specific time range
    ./plot_history.py --instrument BTCUSDT@binance --timeframe 1m --start 1735516800 [--end 1735520400]

    # Fetch last N bars from now
    ./plot_history.py --instrument BTCUSDT@binance --timeframe 1m --last 100

Examples:
    python3 scripts/plot_history.py --instrument BTCUSDT@binance --timeframe 1m --start 1735516800 --end 1735520400
    python3 scripts/plot_history.py --instrument ETHUSDT@binance --timeframe 5m --last 200
"""

import argparse
import json
import sys
import time
import tkinter as tk
from datetime import datetime
from http.client import HTTPConnection
from urllib.parse import urlencode


def fetch_history(host, port, instrument, timeframe, start_ts, end_ts):
    """Fetch historical candle data from the price server."""
    params = {
        'symbol': instrument,
        'interval': timeframe,
        'from': start_ts,
        'to': end_ts
    }

    query_string = urlencode(params)
    url = f"/history?{query_string}"

    print(f"Fetching data from http://{host}:{port}{url}")

    try:
        conn = HTTPConnection(host, port, timeout=10)
        conn.request("GET", url)
        response = conn.getresponse()

        if response.status != 200:
            print(f"Error: HTTP {response.status} - {response.reason}", file=sys.stderr)
            return None

        data = response.read().decode('utf-8')
        conn.close()

        result = json.loads(data)

        if result.get('s') == 'error':
            print(f"Error: {result.get('errmsg', 'Unknown error')}", file=sys.stderr)
            return None

        return result

    except Exception as e:
        print(f"Error fetching data: {e}", file=sys.stderr)
        return None


def plot_gui_chart(data, instrument, timeframe):
    """Plot OHLCV data using tkinter GUI."""
    times = data.get('t', [])
    opens = data.get('o', [])
    highs = data.get('h', [])
    lows = data.get('l', [])
    closes = data.get('c', [])
    volumes = data.get('v', [])

    if not times:
        print("No data to plot", file=sys.stderr)
        return

    # Print statistics to console
    all_prices = opens + highs + lows + closes
    min_price = min(all_prices)
    max_price = max(all_prices)
    price_range = max_price - min_price
    max_volume = max(volumes) if volumes else 0

    print(f"\n{'='*80}")
    print(f"Instrument: {instrument} | Timeframe: {timeframe}")
    print(f"Data points: {len(times)}")
    print(f"Time range: {datetime.fromtimestamp(times[0])} to {datetime.fromtimestamp(times[-1])}")
    print(f"Price range: {min_price:.2f} - {max_price:.2f}")
    print(f"Max volume: {max_volume:,.0f}")
    print(f"{'='*80}\n")

    # Create GUI window
    root = tk.Tk()
    root.title(f"{instrument} - {timeframe}")
    root.geometry("1200x800")

    # Create canvas for price chart
    canvas_height = 500
    canvas_width = 1150
    margin_left = 80
    margin_right = 20
    margin_top = 40
    margin_bottom = 40

    price_canvas = tk.Canvas(root, width=canvas_width + margin_left + margin_right,
                            height=canvas_height + margin_top + margin_bottom, bg='white')
    price_canvas.pack(pady=10)

    # Create canvas for volume chart
    volume_canvas_height = 150
    volume_canvas = tk.Canvas(root, width=canvas_width + margin_left + margin_right,
                             height=volume_canvas_height + 40, bg='white')
    volume_canvas.pack(pady=10)

    # Calculate drawing parameters
    plot_width = canvas_width - margin_left - margin_right
    plot_height = canvas_height - margin_top - margin_bottom

    candle_width = max(1, plot_width // len(times))
    if candle_width > 20:
        candle_width = 20
    candle_spacing = plot_width / len(times)

    # Draw title
    price_canvas.create_text(canvas_width // 2 + margin_left, 20,
                            text=f"{instrument} - {timeframe} OHLC Chart",
                            font=('Arial', 14, 'bold'))

    # Draw price chart grid and labels
    num_price_lines = 10
    for i in range(num_price_lines + 1):
        y = margin_top + (i * plot_height / num_price_lines)
        price = max_price - (i * price_range / num_price_lines)

        # Grid line
        price_canvas.create_line(margin_left, y, margin_left + plot_width, y,
                                fill='#e0e0e0', width=1)

        # Price label
        price_canvas.create_text(margin_left - 10, y, text=f"{price:.2f}",
                                anchor='e', font=('Arial', 9))

    # Draw time labels
    num_time_labels = min(10, len(times))
    for i in range(num_time_labels):
        idx = int(i * (len(times) - 1) / (num_time_labels - 1)) if num_time_labels > 1 else 0
        x = margin_left + idx * candle_spacing
        time_str = datetime.fromtimestamp(times[idx]).strftime('%H:%M')
        price_canvas.create_text(x, canvas_height + margin_top + 15, text=time_str,
                                angle=45, font=('Arial', 8))

    # Function to convert price to y coordinate
    def price_to_y(price):
        if price_range == 0:
            return margin_top + plot_height / 2
        return margin_top + plot_height - ((price - min_price) / price_range * plot_height)

    # Draw candlesticks
    for i, (o, h, l, c) in enumerate(zip(opens, highs, lows, closes)):
        x = margin_left + i * candle_spacing

        # High-low line
        y_high = price_to_y(h)
        y_low = price_to_y(l)
        price_canvas.create_line(x + candle_width/2, y_high, x + candle_width/2, y_low,
                                fill='black', width=1)

        # Open-close body
        y_open = price_to_y(o)
        y_close = price_to_y(c)

        if c >= o:
            # Green candle (close >= open)
            color = '#26a69a'
            outline = '#26a69a'
        else:
            # Red candle (close < open)
            color = '#ef5350'
            outline = '#ef5350'

        y_top = min(y_open, y_close)
        y_bottom = max(y_open, y_close)

        if y_bottom - y_top < 1:
            y_bottom = y_top + 1

        price_canvas.create_rectangle(x, y_top, x + candle_width, y_bottom,
                                      fill=color, outline=outline)

    # Draw volume chart
    volume_canvas.create_text(canvas_width // 2 + margin_left, 15,
                             text="Volume",
                             font=('Arial', 12, 'bold'))

    volume_plot_height = volume_canvas_height - 40

    # Volume grid and labels
    num_vol_lines = 5
    for i in range(num_vol_lines + 1):
        y = 30 + (i * volume_plot_height / num_vol_lines)
        vol = max_volume - (i * max_volume / num_vol_lines)

        volume_canvas.create_line(margin_left, y, margin_left + plot_width, y,
                                 fill='#e0e0e0', width=1)

        volume_canvas.create_text(margin_left - 10, y, text=f"{vol:,.0f}",
                                 anchor='e', font=('Arial', 8))

    # Draw volume bars
    for i, vol in enumerate(volumes):
        x = margin_left + i * candle_spacing
        bar_height = (vol / max_volume * volume_plot_height) if max_volume > 0 else 0
        y_top = 30 + volume_plot_height - bar_height

        # Use same color as candle
        if closes[i] >= opens[i]:
            color = '#26a69a'
        else:
            color = '#ef5350'

        volume_canvas.create_rectangle(x, y_top, x + candle_width, 30 + volume_plot_height,
                                       fill=color, outline=color)

    # Add statistics panel
    stats_frame = tk.Frame(root, bg='#f5f5f5')
    stats_frame.pack(pady=5, padx=10, fill='x')

    stats_text = (
        f"First: O:{opens[0]:.2f} H:{highs[0]:.2f} L:{lows[0]:.2f} C:{closes[0]:.2f} | "
        f"Last: O:{opens[-1]:.2f} H:{highs[-1]:.2f} L:{lows[-1]:.2f} C:{closes[-1]:.2f} | "
        f"Bars: {len(times)}"
    )

    tk.Label(stats_frame, text=stats_text, font=('Arial', 10), bg='#f5f5f5').pack()

    root.mainloop()


def parse_timeframe_to_seconds(timeframe):
    """Convert timeframe string (e.g., '1m', '5m', '1h') to seconds."""
    if not timeframe:
        raise ValueError("Timeframe cannot be empty")

    unit = timeframe[-1]
    try:
        value = int(timeframe[:-1])
    except ValueError:
        raise ValueError(f"Invalid timeframe format: {timeframe}")

    multipliers = {
        's': 1,
        'm': 60,
        'h': 3600,
        'd': 86400
    }

    if unit not in multipliers:
        raise ValueError(f"Invalid timeframe unit: {unit}. Must be s, m, h, or d")

    return value * multipliers[unit]


def main():
    parser = argparse.ArgumentParser(
        description='Fetch and plot historical OHLCV candle data from the price server',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__
    )

    parser.add_argument('--instrument', required=True,
                        help='Instrument symbol (e.g., BTCUSDT@binance)')
    parser.add_argument('--timeframe', required=True,
                        help='Timeframe interval (e.g., 1m, 5m, 1h)')
    parser.add_argument('--start', type=int,
                        help='Start timestamp in seconds (Unix epoch)')
    parser.add_argument('--end', type=int,
                        help='End timestamp in seconds (Unix epoch). Defaults to now if not provided')
    parser.add_argument('--last', type=int,
                        help='Fetch last N bars from now (alternative to --start/--end)')
    parser.add_argument('--host', default='localhost',
                        help='Price server host (default: localhost)')
    parser.add_argument('--port', type=int, default=8080,
                        help='Price server port (default: 8080)')

    args = parser.parse_args()

    # Validate arguments
    if args.last and args.start:
        print("Error: Cannot use both --last and --start options", file=sys.stderr)
        sys.exit(1)

    if not args.last and not args.start:
        print("Error: Must provide either --start or --last option", file=sys.stderr)
        sys.exit(1)

    # Calculate time range
    if args.last:
        # Fetch last N bars from now
        end_ts = int(time.time())
        timeframe_seconds = parse_timeframe_to_seconds(args.timeframe)
        start_ts = end_ts - (args.last * timeframe_seconds)
        print(f"Fetching last {args.last} bars ({args.timeframe}) from now")
    else:
        # Use provided start/end timestamps
        start_ts = args.start
        end_ts = args.end if args.end else int(time.time())

        if start_ts >= end_ts:
            print("Error: Start timestamp must be before end timestamp", file=sys.stderr)
            sys.exit(1)

    # Fetch data
    data = fetch_history(args.host, args.port, args.instrument, args.timeframe, start_ts, end_ts)

    if data is None:
        sys.exit(1)

    # Plot data
    plot_gui_chart(data, args.instrument, args.timeframe)


if __name__ == '__main__':
    main()
