import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from './api.config';
import { Page, Producto, ProductoCodigo, ProductoListItem } from './models';

@Injectable({ providedIn: 'root' })
export class ProductoService {
  private http = inject(HttpClient);
  private base = `${API_BASE_URL}/productos`;

  listar(page = 0, size = 10): Observable<Page<ProductoListItem>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<ProductoListItem>>(this.base, { params });
  }

  /** Búsqueda multi-característica: matchea descripción, SKU, marca o categoría. */
  buscarTexto(q: string, page = 0, size = 10): Observable<Page<ProductoListItem>> {
    const params = new HttpParams().set('q', q).set('page', page).set('size', size);
    return this.http.get<Page<ProductoListItem>>(`${this.base}/buscar-texto`, { params });
  }

  getById(id: number): Observable<Producto> {
    return this.http.get<Producto>(`${this.base}/${id}`);
  }

  buscarPorCodigo(codigo: string): Observable<Producto> {
    const params = new HttpParams().set('codigo', codigo);
    return this.http.get<Producto>(`${this.base}/buscar`, { params });
  }

  crear(req: {
    sku?: string;
    marcaId?: number;
    marcaNombre?: string;
    categoriaId?: number;
    descripcion: string;
    codigos?: ProductoCodigo[];
  }): Observable<Producto> {
    return this.http.post<Producto>(this.base, req);
  }

  /** Edita los campos de catálogo de un producto existente. */
  editar(id: number, req: {
    sku?: string;
    marcaId?: number;
    marcaNombre?: string;
    categoriaId?: number;
    descripcion: string;
  }): Observable<Producto> {
    return this.http.put<Producto>(`${this.base}/${id}`, req);
  }

  /** Asocia un código (ej: código de barras) a un producto existente. */
  agregarCodigo(productoId: number, codigo: string, tipo = 'BARRA'): Observable<Producto> {
    return this.http.post<Producto>(`${this.base}/${productoId}/codigos`, { codigo, tipo });
  }

  /** Baja lógica: marca el producto como INACTIVO (conserva el historial). */
  darDeBaja(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
