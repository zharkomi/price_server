import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ConfigResponse } from '../models/config.model';

@Injectable({
  providedIn: 'root'
})
export class ConfigService {
  private readonly apiUrl = '/api/config';

  constructor(private http: HttpClient) {}

  getConfig(): Observable<ConfigResponse> {
    return this.http.get<ConfigResponse>(this.apiUrl);
  }
}
