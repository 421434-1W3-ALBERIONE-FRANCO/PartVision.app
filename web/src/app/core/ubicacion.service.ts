import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from './api.config';
import { Ubicacion } from './models';

export interface UbicacionInput {
  codigo: string;
  descripcion?: string | null;
}

@Injectable({ providedIn: 'root' })
export class UbicacionService {
  private http = inject(HttpClient);
  private base = `${API_BASE_URL}/ubicaciones`;

  /** Lista todas las ubicaciones (modelo plano). */
  listar(): Observable<Ubicacion[]> {
    return this.http.get<Ubicacion[]>(this.base);
  }

  crear(req: UbicacionInput): Observable<Ubicacion> {
    return this.http.post<Ubicacion>(this.base, req);
  }

  actualizar(id: number, req: UbicacionInput): Observable<Ubicacion> {
    return this.http.put<Ubicacion>(`${this.base}/${id}`, req);
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
