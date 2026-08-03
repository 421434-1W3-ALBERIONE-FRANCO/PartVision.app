import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from './api.config';
import { ImportResult } from './models';

@Injectable({ providedIn: 'root' })
export class ImportService {
  private http = inject(HttpClient);

  importarProductos(archivo: File): Observable<ImportResult> {
    const form = new FormData();
    form.append('archivo', archivo, archivo.name);
    return this.http.post<ImportResult>(`${API_BASE_URL}/importaciones/productos`, form);
  }
}
