import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from './api.config';
import { AiExtraction, Page, Producto, ProductoCodigo, SugerenciaAccion } from './models';

export interface ConfirmarExtraccion {
  producto: {
    sku?: string;
    marcaId?: number;
    marcaNombre?: string;
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

  /** Sube una imagen y crea un borrador PENDIENTE con lo que detectó la IA. */
  extraer(archivo: File): Observable<AiExtraction> {
    const form = new FormData();
    form.append('archivo', archivo, archivo.name);
    return this.http.post<AiExtraction>(this.base, form);
  }

  /** Sugerencia de acción (nuevo / ya existe / agregar código) según el catálogo. */
  sugerencia(id: number): Observable<SugerenciaAccion> {
    return this.http.get<SugerenciaAccion>(`${this.base}/${id}/sugerencia`);
  }

  listar(estado = 'PENDIENTE', page = 0, size = 20): Observable<Page<AiExtraction>> {
    const params = new HttpParams().set('estado', estado).set('page', page).set('size', size);
    return this.http.get<Page<AiExtraction>>(this.base, { params });
  }

  confirmar(id: number, req: ConfirmarExtraccion): Observable<{ producto: Producto }> {
    return this.http.post<{ producto: Producto }>(`${this.base}/${id}/confirmar`, req);
  }

  /** Asocia el código de barras detectado a un producto existente. */
  asociarCodigo(id: number, productoId: number): Observable<AiExtraction> {
    return this.http.post<AiExtraction>(`${this.base}/${id}/asociar-codigo`, { productoId });
  }

  descartar(id: number): Observable<AiExtraction> {
    return this.http.post<AiExtraction>(`${this.base}/${id}/descartar`, {});
  }
}
