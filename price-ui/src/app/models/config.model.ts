export interface Instrument {
  name: string;
  source: string;
  timeframes: string[];
}

export interface ConfigResponse {
  status: string;
  service: string;
  instruments: Instrument[];
  timestamp: number;
}

export interface HistoryResponse {
  s: string;
  errmsg: string | null;
  t: number[];
  o: number[];
  h: number[];
  l: number[];
  c: number[];
  v: number[];
}

export interface CandleUpdate {
  i: string;  // instrument
  t: number;  // time
  f: number;  // timeframe ms
  o: number;  // open
  h: number;  // high
  l: number;  // low
  c: number;  // close
  v: number;  // volume
}
