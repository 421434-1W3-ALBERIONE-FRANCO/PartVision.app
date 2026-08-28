import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from './api.config';
import { ConfiguracionPrecio, Page, PrecioBatch, PrecioImportColumnas, PrecioImportPreview, PrecioImportResult, Producto, ProductoCodigo, ProductoListItem, SyncResult } from './models';

@Injectable({ providedIn: 'root' })
export class ProductoService {
  private http = inject(HttpClient);
  private base = `${API_BASE_URL}/productos`;

  listar(page = 0, size = 10, conStock?: boolean, q?: string, sort?: string): Observable<Page<ProductoListItem>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (conStock !== undefined) params = params.set('conStock', conStock);
    if (q) params = params.set('q', q);
    if (sort) params = params.set('sort', sort);
    return this.http.get<Page<ProductoListItem>>(this.base, { params });
  }

  /** Búsqueda multi-característica: matchea descripción, SKU, marca o categoría. */
  buscarTexto(q: string, page = 0, size = 10, sort?: string): Observable<Page<ProductoListItem>> {
    let params = new HttpParams().set('q', q).set('page', page).set('size', size);
    if (sort) params = params.set('sort', sort);
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

  sincronizarPrecios(): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${API_BASE_URL}/precios/sync`, {});
  }

  estadoSync(): Observable<{ sincronizando: boolean; ultimoResultado?: SyncResult }> {
    return this.http.get<{ sincronizando: boolean; ultimoResultado?: SyncResult }>(`${API_BASE_URL}/precios/sync/estado`);
  }

  listarConfigPrecios(): Observable<ConfiguracionPrecio[]> {
    return this.http.get<ConfiguracionPrecio[]>(`${API_BASE_URL}/precios/configuracion`);
  }

  actualizarConfigPrecio(id: number, margen: number, activo: boolean): Observable<ConfiguracionPrecio> {
    return this.http.put<ConfiguracionPrecio>(`${API_BASE_URL}/precios/configuracion/${id}`, { margen, activo });
  }

  crearConfigPrecio(proveedor: string, margen: number): Observable<ConfiguracionPrecio> {
    return this.http.post<ConfiguracionPrecio>(`${API_BASE_URL}/precios/configuracion`, { proveedor, margen, activo: true });
  }

  importDetectarColumnas(archivo: File): Observable<PrecioImportColumnas> {
    const fd = new FormData();
    fd.append('archivo', archivo);
    return this.http.post<PrecioImportColumnas>(`${API_BASE_URL}/precios/import/columnas`, fd);
  }

  importPreview(uploadId: string, colSku: string, colPrecio: string, proveedor: string): Observable<PrecioImportPreview> {
    const params = new HttpParams()
      .set('uploadId', uploadId).set('colSku', colSku)
      .set('colPrecio', colPrecio).set('proveedor', proveedor);
    return this.http.post<PrecioImportPreview>(`${API_BASE_URL}/precios/import/preview`, null, { params });
  }

  importAplicar(uploadId: string, colSku: string, colPrecio: string, proveedor: string,
                excluidos: string[], archivo: string): Observable<PrecioImportResult> {
    let params = new HttpParams()
      .set('uploadId', uploadId).set('colSku', colSku)
      .set('colPrecio', colPrecio).set('proveedor', proveedor)
      .set('archivo', archivo);
    for (const sku of excluidos) {
      params = params.append('excluidos', sku);
    }
    return this.http.post<PrecioImportResult>(`${API_BASE_URL}/precios/import/aplicar`, null, { params });
  }

  listarBatches(): Observable<PrecioBatch[]> {
    return this.http.get<PrecioBatch[]>(`${API_BASE_URL}/precios/import/batches`);
  }

  rollbackBatch(id: number): Observable<PrecioBatch> {
    return this.http.post<PrecioBatch>(`${API_BASE_URL}/precios/import/batches/${id}/rollback`, {});
  }
}
