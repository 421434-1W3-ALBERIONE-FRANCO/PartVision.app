import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { API_BASE_URL } from './api.config';
import { AlertaPrecios, PrecioRevision, RevisionPreciosResultado, SincronizacionEstado, SincronizacionPrecios } from './models';

/** Actualizacion automatica de precios desde el portal del proveedor (ADS). */
@Injectable({ providedIn: 'root' })
export class PrecioSyncService {
  private http = inject(HttpClient);
  private base = `${API_BASE_URL}/precios/sincronizacion`;

  estado(): Observable<SincronizacionEstado> {
    return this.http.get<SincronizacionEstado>(this.base);
  }

  /** "Actualizar ahora"; con forzar aplica una lista que habia quedado retenida. */
  actualizarAhora(forzar = false): Observable<SincronizacionPrecios> {
    const params = new HttpParams().set('forzar', forzar);
    return this.http.post<SincronizacionPrecios>(this.base, null, { params });
  }

  /** null cuando no hay nada que mirar (el backend responde 204). */
  alerta(): Observable<AlertaPrecios | null> {
    return this.http.get<AlertaPrecios>(`${this.base}/alerta`, { observe: 'response' })
      .pipe(map((r: HttpResponse<AlertaPrecios>) => r.status === 204 ? null : r.body));
  }

  revisiones(): Observable<PrecioRevision[]> {
    return this.http.get<PrecioRevision[]>(`${this.base}/revisiones`);
  }

  aplicar(ids: number[]): Observable<RevisionPreciosResultado> {
    return this.http.post<RevisionPreciosResultado>(`${this.base}/revisiones/aplicar`, { ids });
  }

  descartar(ids: number[]): Observable<RevisionPreciosResultado> {
    return this.http.post<RevisionPreciosResultado>(`${this.base}/revisiones/descartar`, { ids });
  }
}
