import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { HistoryResponse } from '../models/config.model';

@Injectable({
  providedIn: 'root'
})
export class HistoryService {
  private readonly apiUrl = '/api/history';

  constructor(private http: HttpClient) {}

  getHistory(symbol: string, interval: string, from: number, to: number): Observable<HistoryResponse> {
    const params = new HttpParams()
      .set('symbol', symbol)
      .set('interval', interval)
      .set('from', from.toString())
      .set('to', to.toString());

    return this.http.get<HistoryResponse>(this.apiUrl, { params });
  }
}
