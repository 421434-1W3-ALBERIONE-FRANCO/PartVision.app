import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from './api.config';
import { ImportJob } from './models';

@Injectable({ providedIn: 'root' })
export class ImportService {
  private http = inject(HttpClient);
  private base = `${API_BASE_URL}/importaciones/productos`;

  /** Inicia la importación asíncrona; devuelve el job (con jobId) para seguir el progreso. */
  importarProductos(archivo: File): Observable<ImportJob> {
    const form = new FormData();
    form.append('archivo', archivo, archivo.name);
    return this.http.post<ImportJob>(this.base, form);
  }

  /** Estado/progreso de una importación en curso. */
  estado(jobId: string): Observable<ImportJob> {
    return this.http.get<ImportJob>(`${this.base}/${jobId}`);
  }
}
