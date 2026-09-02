import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from './api.config';
import { Compra, Page } from './models';

@Injectable({ providedIn: 'root' })
export class CompraService {
  private http = inject(HttpClient);
  private base = `${API_BASE_URL}/compras`;

  listar(page = 0, size = 20, estado?: string): Observable<Page<Compra>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (estado) params = params.set('estado', estado);
    return this.http.get<Page<Compra>>(this.base, { params });
  }

  detalle(id: number): Observable<Compra> {
    return this.http.get<Compra>(`${this.base}/${id}`);
  }

  marcarIngresada(id: number, ubicacionId: number): Observable<Compra> {
    return this.http.patch<Compra>(`${this.base}/${id}/ingresar`, { ubicacionId });
  }
}
