// Interfaces que reflejan exactamente los DTOs del backend.

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface LoginResponse {
  token: string;
  expiresIn: number;
}

export interface Marca {
  id: number;
  nombre: string;
}

export interface ProductoCodigo {
  id?: number;
  codigo: string;
  tipo?: string | null;
}

export interface Producto {
  id: number;
  sku: string | null;
  marcaId: number | null;
  marcaNombre: string | null;
  categoriaId: number | null;
  categoriaNombre: string | null;
  descripcion: string;
  estado: string;
  detallesExtra: Record<string, unknown>;
  codigos: ProductoCodigo[];
}

export interface ProductoListItem {
  id: number;
  sku: string | null;
  descripcion: string;
  estado: string;
  marcaNombre: string | null;
  categoriaNombre: string | null;
}

export interface StockLinea {
  ubicacionId: number;
  ubicacionPath: string;
  cantidad: number;
}

export interface StockResumen {
  productoId: number;
  total: number;
  ubicaciones: StockLinea[];
}

export interface ConteoResponse {
  productoId: number;
  ubicacionId: number;
  cantidadAnterior: number;
  cantidadNueva: number;
  movimiento: Movimiento | null;
}

export interface Movimiento {
  id: number;
  productoId: number;
  tipo: string;
  cantidad: number;
  ubicacionOrigenId: number | null;
  ubicacionDestinoId: number | null;
  usuarioId: number | null;
  motivo: string | null;
  fecha: string | null;
}

export interface Ubicacion {
  id: number;
  tipo: string | null;
  codigo: string;
  descripcion: string | null;
  path: string;
  activo: boolean;
  parentId: number | null;
}

export interface AiExtraction {
  id: number;
  imagenKey: string;
  modelo: string;
  estado: string;
  datosSugeridos: Record<string, unknown>;
  productoId: number | null;
}

export type AccionSugerida = 'NUEVO' | 'YA_EXISTE' | 'AGREGAR_CODIGO' | 'POSIBLES_COINCIDENCIAS';

export interface CandidatoCoincidencia {
  id: number;
  sku: string | null;
  descripcion: string;
  marca: string | null;
  proveedor: string | null;
}

export interface SugerenciaAccion {
  accion: AccionSugerida;
  productoExistenteId: number | null;
  productoExistenteDescripcion: string | null;
  codigoBarras: string | null;
  mensaje: string;
  candidatos: CandidatoCoincidencia[];
}

export interface ImportResult {
  totalFilas: number;
  importados: number;
  omitidos: number;
  errores: { fila: number; mensaje: string }[];
}

export interface Usuario {
  id: number;
  username: string;
  nombre: string;
  activo: boolean;
  roles: string[];
}
