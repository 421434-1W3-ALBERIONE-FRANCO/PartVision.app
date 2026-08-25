import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from './api.config';
import { EstadoOcupacion, StockDetalleUbicacion, StockPorUbicacion, Ubicacion } from './models';

export interface UbicacionInput {
  codigo: string;
  descripcion?: string | null;
  tipo?: string | null;
  parentId?: number | null;
}

@Injectable({ providedIn: 'root' })
export class UbicacionService {
  private http = inject(HttpClient);
  private base = `${API_BASE_URL}/ubicaciones`;

  listar(): Observable<Ubicacion[]> {
    return this.http.get<Ubicacion[]>(this.base);
  }

  stockResumen(): Observable<Record<number, StockPorUbicacion>> {
    return this.http.get<Record<number, StockPorUbicacion>>(`${this.base}/stock-resumen`);
  }

  crear(req: UbicacionInput): Observable<Ubicacion> {
    return this.http.post<Ubicacion>(this.base, req);
  }

  actualizar(id: number, req: UbicacionInput): Observable<Ubicacion> {
    return this.http.put<Ubicacion>(`${this.base}/${id}`, req);
  }

  stockDetalle(ubicacionId: number): Observable<StockDetalleUbicacion[]> {
    return this.http.get<StockDetalleUbicacion[]>(`${this.base}/${ubicacionId}/stock-detalle`);
  }

  cambiarEstadoOcupacion(id: number, estado: EstadoOcupacion): Observable<Ubicacion> {
    return this.http.patch<Ubicacion>(`${this.base}/${id}/estado-ocupacion`, null, { params: { estado } });
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
