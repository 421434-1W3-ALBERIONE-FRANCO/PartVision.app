import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from './api.config';
import { Compra, ImportadoPendiente, ImportadoResuelto, Page } from './models';

export interface LineaUbicacionAsignacion {
  lineaId: number;
  ubicacionId: number;
}

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

  marcarIngresada(id: number, asignaciones: LineaUbicacionAsignacion[]): Observable<Compra> {
    return this.http.patch<Compra>(`${this.base}/${id}/ingresar`, { asignaciones });
  }

  /**
   * Cambia el estado a mano, sin esperar a la planilla. Volver atras una compra ingresada
   * devuelve el stock que habia cargado, asi que el backend puede rechazarlo si ya no esta.
   */
  cambiarEstado(id: number, estado: 'EN_TRANSITO' | 'POR_UBICAR'): Observable<Compra> {
    return this.http.patch<Compra>(`${this.base}/${id}/estado`, { estado });
  }

  // --- importados: lineas que llegaron sin codigo ---

  importados(page = 0, size = 20): Observable<Page<ImportadoPendiente>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<ImportadoPendiente>>(`${this.base}/importados`, { params });
  }

  skuSugerido(): Observable<{ sku: string }> {
    return this.http.get<{ sku: string }>(`${this.base}/importados/sku-sugerido`);
  }

  darDeAltaImportado(lineaId: number, sku: string, descripcion: string, ubicacionId: number | null):
      Observable<ImportadoResuelto> {
    return this.http.post<ImportadoResuelto>(`${this.base}/importados/${lineaId}/alta`,
      { sku, descripcion, ubicacionId });
  }

  vincularImportado(lineaId: number, productoId: number, ubicacionId: number | null):
      Observable<ImportadoResuelto> {
    return this.http.post<ImportadoResuelto>(`${this.base}/importados/${lineaId}/vincular`,
      { productoId, ubicacionId });
  }
}
