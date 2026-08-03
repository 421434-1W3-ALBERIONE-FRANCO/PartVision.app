import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from './api.config';
import { AiExtraction, Page, Producto, ProductoCodigo } from './models';

export interface ConfirmarExtraccion {
  producto: {
    sku?: string;
    marcaId?: number;
    descripcion: string;
    codigos?: ProductoCodigo[];
  };
  ubicacionId?: number;
  cantidad?: number;
}

@Injectable({ providedIn: 'root' })
export class ExtraccionService {
  private http = inject(HttpClient);
  private base = `${API_BASE_URL}/extracciones`;

  listar(estado = 'PENDIENTE', page = 0, size = 20): Observable<Page<AiExtraction>> {
    const params = new HttpParams().set('estado', estado).set('page', page).set('size', size);
    return this.http.get<Page<AiExtraction>>(this.base, { params });
  }

  confirmar(id: number, req: ConfirmarExtraccion): Observable<{ producto: Producto }> {
    return this.http.post<{ producto: Producto }>(`${this.base}/${id}/confirmar`, req);
  }

  descartar(id: number): Observable<AiExtraction> {
    return this.http.post<AiExtraction>(`${this.base}/${id}/descartar`, {});
  }
}
