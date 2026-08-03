import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from './api.config';
import { Ubicacion } from './models';

@Injectable({ providedIn: 'root' })
export class UbicacionService {
  private http = inject(HttpClient);
  private base = `${API_BASE_URL}/ubicaciones`;

  raices(): Observable<Ubicacion[]> {
    return this.http.get<Ubicacion[]>(this.base);
  }

  hijos(id: number): Observable<Ubicacion[]> {
    return this.http.get<Ubicacion[]>(`${this.base}/${id}/hijos`);
  }

  crear(req: { tipo: string; codigo: string; parentId?: number }): Observable<Ubicacion> {
    return this.http.post<Ubicacion>(this.base, req);
  }
}
