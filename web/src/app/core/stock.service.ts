import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from './api.config';
import { ConteoResponse, Movimiento, Page, StockResumen } from './models';

export interface ConteoLinea {
  productoId: number;
  ubicacionId: number;
  cantidadReal: number;
  motivo?: string;
}

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

  /** Conteo físico en lote: fija las cantidades reales de varias líneas. */
  conteoLote(conteos: ConteoLinea[]): Observable<ConteoResponse[]> {
    return this.http.post<ConteoResponse[]>(`${API_BASE_URL}/stock/conteos/lote`, { conteos });
  }

  /** Entrada: suma cantidad de un producto en una ubicación. */
  entrada(req: { productoId: number; ubicacionId: number; cantidad: number; motivo?: string }): Observable<Movimiento> {
    return this.http.post<Movimiento>(`${API_BASE_URL}/stock/entradas`, req);
  }

  /** Conteo: fija la cantidad EXACTA de un producto en una ubicación (no suma). */
  conteo(req: { productoId: number; ubicacionId: number; cantidadReal: number; motivo?: string }): Observable<ConteoResponse> {
    return this.http.post<ConteoResponse>(`${API_BASE_URL}/stock/conteos`, req);
  }

  /** Elimina la existencia de un producto en una ubicación. */
  eliminar(productoId: number, ubicacionId: number): Observable<void> {
    const params = new HttpParams().set('productoId', productoId).set('ubicacionId', ubicacionId);
    return this.http.delete<void>(`${API_BASE_URL}/stock`, { params });
  }
}
