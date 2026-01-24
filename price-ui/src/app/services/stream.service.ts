import { Injectable } from '@angular/core';
import { Subject, Observable } from 'rxjs';
import { CandleUpdate } from '../models/config.model';

@Injectable({
  providedIn: 'root'
})
export class StreamService {
  private socket: WebSocket | null = null;
  private messageSubject = new Subject<CandleUpdate[]>();
  private connectionSubject = new Subject<boolean>();

  get messages$(): Observable<CandleUpdate[]> {
    return this.messageSubject.asObservable();
  }

  get connected$(): Observable<boolean> {
    return this.connectionSubject.asObservable();
  }

  connect(): void {
    if (this.socket) {
      return;
    }

    const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${wsProtocol}//${window.location.hostname}:8081/stream`;

    this.socket = new WebSocket(wsUrl);

    this.socket.onopen = () => {
      console.log('WebSocket connected');
      this.connectionSubject.next(true);
    };

    this.socket.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data) as CandleUpdate[];
        this.messageSubject.next(data);
      } catch (e) {
        console.error('Failed to parse WebSocket message:', e);
      }
    };

    this.socket.onclose = () => {
      console.log('WebSocket disconnected');
      this.connectionSubject.next(false);
      this.socket = null;
    };

    this.socket.onerror = (error) => {
      console.error('WebSocket error:', error);
    };
  }

  disconnect(): void {
    if (this.socket) {
      this.socket.close();
      this.socket = null;
    }
  }

  subscribe(instrument: string, timeframeMs: number): void {
    if (this.socket && this.socket.readyState === WebSocket.OPEN) {
      const message = {
        type: 'SUBSCRIBE',
        instrument: instrument,
        timeframe: timeframeMs
      };
      this.socket.send(JSON.stringify(message));
      console.log('Subscribed to:', instrument, timeframeMs);
    }
  }

  unsubscribe(instrument: string, timeframeMs: number): void {
    if (this.socket && this.socket.readyState === WebSocket.OPEN) {
      const message = {
        type: 'UNSUBSCRIBE',
        instrument: instrument,
        timeframe: timeframeMs
      };
      this.socket.send(JSON.stringify(message));
      console.log('Unsubscribed from:', instrument, timeframeMs);
    }
  }
}
