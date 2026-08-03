import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from './api.config';
import { Movimiento, Page, StockResumen } from './models';

@Injectable({ providedIn: 'root' })
export class StockService {
  private http = inject(HttpClient);

  resumen(productoId: number): Observable<StockResumen> {
    const params = new HttpParams().set('productoId', productoId);
    return this.http.get<StockResumen>(`${API_BASE_URL}/stock`, { params });
  }

  movimientos(productoId: number, page = 0, size = 20): Observable<Page<Movimiento>> {
    const params = new HttpParams().set('productoId', productoId).set('page', page).set('size', size);
    return this.http.get<Page<Movimiento>>(`${API_BASE_URL}/movimientos`, { params });
  }
}
