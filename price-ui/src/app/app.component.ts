import { Component, OnInit, OnDestroy, ViewChild, ElementRef, AfterViewInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription, filter, take } from 'rxjs';
import { createChart, IChartApi, ISeriesApi, CandlestickData, Time } from 'lightweight-charts';
import { ConfigService } from './services/config.service';
import { HistoryService } from './services/history.service';
import { StreamService } from './services/stream.service';
import { Instrument, CandleUpdate } from './models/config.model';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit, OnDestroy, AfterViewInit {
  @ViewChild('chartContainer') chartContainer!: ElementRef<HTMLDivElement>;

  exchanges: string[] = [];
  instruments: Instrument[] = [];
  filteredInstruments: string[] = [];
  timeframes: string[] = [];

  selectedExchange = '';
  selectedInstrument = '';
  selectedTimeframe = '';
  preloadBars = 100;

  isRunning = false;
  isLoading = false;

  private chart: IChartApi | null = null;
  private candleSeries: ISeriesApi<'Candlestick'> | null = null;
  private subscriptions: Subscription[] = [];
  private connectionSub: Subscription | null = null;
  private candleData: Map<number, CandlestickData<Time>> = new Map();
  private activeInstrumentKey = '';
  private activeTimeframeMs = 0;

  constructor(
    private configService: ConfigService,
    private historyService: HistoryService,
    private streamService: StreamService
  ) {}

  ngOnInit(): void {
    this.loadConfig();
    this.setupStreamSubscription();
  }

  ngAfterViewInit(): void {
    this.initChart();
    this.handleResize();
  }

  ngOnDestroy(): void {
    this.stop();
    this.subscriptions.forEach(s => s.unsubscribe());
    if (this.connectionSub) {
      this.connectionSub.unsubscribe();
    }
    if (this.chart) {
      this.chart.remove();
    }
  }

  private loadConfig(): void {
    this.configService.getConfig().subscribe({
      next: (config) => {
        this.instruments = config.instruments;
        this.exchanges = [...new Set(config.instruments.map(i => i.source))];

        if (this.exchanges.length > 0) {
          this.selectedExchange = this.exchanges[0];
          this.onExchangeChange();
        }
      },
      error: (err) => {
        console.error('Failed to load config:', err);
      }
    });
  }

  private initChart(): void {
    if (!this.chartContainer) return;

    this.chart = createChart(this.chartContainer.nativeElement, {
      layout: {
        background: { color: '#131722' },
        textColor: '#d1d4dc',
      },
      grid: {
        vertLines: { color: '#1e222d' },
        horzLines: { color: '#1e222d' },
      },
      crosshair: {
        mode: 1,
      },
      rightPriceScale: {
        borderColor: '#2a2e39',
      },
      timeScale: {
        borderColor: '#2a2e39',
        timeVisible: true,
        secondsVisible: false,
      },
    });

    this.candleSeries = this.chart.addCandlestickSeries({
      upColor: '#26a69a',
      downColor: '#ef5350',
      borderDownColor: '#ef5350',
      borderUpColor: '#26a69a',
      wickDownColor: '#ef5350',
      wickUpColor: '#26a69a',
    });
  }

  private handleResize(): void {
    const resizeObserver = new ResizeObserver(() => {
      if (this.chart && this.chartContainer) {
        const rect = this.chartContainer.nativeElement.getBoundingClientRect();
        this.chart.applyOptions({
          width: rect.width,
          height: rect.height,
        });
      }
    });

    if (this.chartContainer) {
      resizeObserver.observe(this.chartContainer.nativeElement);
    }
  }

  private setupStreamSubscription(): void {
    const sub = this.streamService.messages$.subscribe((updates) => {
      this.handleCandleUpdates(updates);
    });
    this.subscriptions.push(sub);
  }

  private handleCandleUpdates(updates: CandleUpdate[]): void {
    if (!this.candleSeries || !this.isRunning) return;

    for (const update of updates) {
      if (update.i === this.activeInstrumentKey && update.f === this.activeTimeframeMs) {
        const timeInSeconds = Math.floor(update.t / 1000) as Time;
        const candle: CandlestickData<Time> = {
          time: timeInSeconds,
          open: update.o,
          high: update.h,
          low: update.l,
          close: update.c,
        };

        this.candleData.set(update.t, candle);
        this.candleSeries.update(candle);
      }
    }
  }

  onExchangeChange(): void {
    this.filteredInstruments = this.instruments
      .filter(i => i.source === this.selectedExchange)
      .map(i => i.name);

    if (this.filteredInstruments.length > 0) {
      this.selectedInstrument = this.filteredInstruments[0];
      this.onInstrumentChange();
    } else {
      this.selectedInstrument = '';
      this.timeframes = [];
    }
  }

  onInstrumentChange(): void {
    const instrument = this.instruments.find(
      i => i.name === this.selectedInstrument && i.source === this.selectedExchange
    );

    if (instrument) {
      this.timeframes = instrument.timeframes;
      if (this.timeframes.length > 0 && !this.timeframes.includes(this.selectedTimeframe)) {
        this.selectedTimeframe = this.timeframes[0];
      }
    } else {
      this.timeframes = [];
    }
  }

  toggleStartStop(): void {
    if (this.isRunning) {
      this.stop();
    } else {
      this.start();
    }
  }

  private async start(): Promise<void> {
    if (!this.selectedInstrument || !this.selectedTimeframe) {
      return;
    }

    this.isLoading = true;
    this.candleData.clear();

    // Capture values at start time
    this.activeInstrumentKey = `${this.selectedInstrument}@${this.selectedExchange}`;
    this.activeTimeframeMs = this.parseTimeframe(this.selectedTimeframe);

    // Store captured values in local constants for the callback
    const instrumentKey = this.activeInstrumentKey;
    const timeframeMs = this.activeTimeframeMs;

    try {
      await this.loadHistory();

      // Clean up any previous connection subscription
      if (this.connectionSub) {
        this.connectionSub.unsubscribe();
        this.connectionSub = null;
      }

      this.streamService.connect();

      // Use take(1) to only handle the first connected event
      this.connectionSub = this.streamService.connected$.pipe(
        filter(connected => connected),
        take(1)
      ).subscribe(() => {
        this.streamService.subscribe(instrumentKey, timeframeMs);
      });

      this.isRunning = true;
    } catch (err) {
      console.error('Failed to start:', err);
    } finally {
      this.isLoading = false;
    }
  }

  private stop(): void {
    if (this.connectionSub) {
      this.connectionSub.unsubscribe();
      this.connectionSub = null;
    }

    if (this.isRunning) {
      this.streamService.unsubscribe(this.activeInstrumentKey, this.activeTimeframeMs);
    }

    this.streamService.disconnect();
    this.isRunning = false;
  }

  private loadHistory(): Promise<void> {
    return new Promise((resolve, reject) => {
      const now = Math.floor(Date.now() / 1000);
      const from = now - (this.preloadBars * this.activeTimeframeMs / 1000);

      this.historyService.getHistory(this.activeInstrumentKey, this.selectedTimeframe, from, now).subscribe({
        next: (response) => {
          if (response.s === 'ok' && response.t && response.t.length > 0) {
            const candles: CandlestickData<Time>[] = [];

            for (let i = 0; i < response.t.length; i++) {
              const candle: CandlestickData<Time> = {
                time: response.t[i] as Time,
                open: response.o[i],
                high: response.h[i],
                low: response.l[i],
                close: response.c[i],
              };
              candles.push(candle);
              this.candleData.set(response.t[i] * 1000, candle);
            }

            if (this.candleSeries) {
              this.candleSeries.setData(candles);
            }
          }
          resolve();
        },
        error: (err) => {
          console.error('Failed to load history:', err);
          reject(err);
        }
      });
    });
  }

  private parseTimeframe(timeframe: string): number {
    return parseInt(timeframe, 10);
  }

}
