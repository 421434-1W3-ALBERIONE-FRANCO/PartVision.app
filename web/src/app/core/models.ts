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

export interface TotpStatus {
  enabled: boolean;
}

export interface TotpSetupResponse {
  secret: string;
  otpauthUri: string;
}

export interface RecoveryCodesResponse {
  recoveryCodes: string[];
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
  precioCosto: number | null;
  precioLista: number | null;
  precioVenta: number | null;
  precioActualizadoEn: string | null;
}

export interface ProductoStockUbicacion {
  codigo: string;
  cantidad: number;
}

export interface ProductoListItem {
  id: number;
  sku: string | null;
  descripcion: string;
  estado: string;
  marcaNombre: string | null;
  categoriaNombre: string | null;
  stockTotal: number;
  ubicaciones: ProductoStockUbicacion[];
  precioCosto: number | null;
  precioVenta: number | null;
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
  referencia: string | null;
  fecha: string | null;
}

export type EstadoOcupacion = 'LIBRE' | 'INTERMEDIA' | 'LLENA';

export interface Ubicacion {
  id: number;
  tipo: string | null;
  codigo: string;
  descripcion: string | null;
  path: string;
  activo: boolean;
  parentId: number | null;
  estadoOcupacion: EstadoOcupacion;
}

export interface StockPorUbicacion {
  ubicacionId: number;
  cantidadTotal: number;
  productosDistintos: number;
}

export interface StockDetalleUbicacion {
  productoId: number;
  sku: string | null;
  descripcion: string;
  marcaNombre: string | null;
  categoriaNombre: string | null;
  cantidad: number;
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

export interface ConfiguracionPrecio {
  id: number;
  proveedor: string;
  margen: number;
  activo: boolean;
  updatedAt: string;
}

export interface PrecioImportColumnas {
  uploadId: string;
  columnas: string[];
  totalFilas: number;
}

export interface PrecioPreviewFila {
  fila: number;
  skuCsv: string;
  precioCostoCsv: number;
  estado: 'OK' | 'CONFLICTO' | 'NO_ENCONTRADO';
  productoId: number | null;
  productoDescripcion: string | null;
  productoMarca: string | null;
  precioActual: number | null;
  precioNuevoCalculado: number | null;
  cantidadMatches: number;
}

export interface PrecioImportPreview {
  filas: PrecioPreviewFila[];
  total: number;
  ok: number;
  conflictos: number;
  noEncontrados: number;
  margenAplicado: number;
}

export interface PrecioImportResult {
  batchId: number;
  total: number;
  aplicados: number;
  omitidos: number;
  conflictos: number;
  mensaje: string;
}

export interface PrecioImportProgreso {
  importando: boolean;
  progreso: number;
  total: number;
  ultimoResultado?: PrecioImportResult;
}

export interface PrecioBatch {
  id: number;
  proveedor: string;
  fuente: string;
  archivo: string | null;
  total: number;
  aplicados: number;
  conflictos: number;
  estado: string;
  createdAt: string;
}

export interface ImportResult {
  totalFilas: number;
  importados: number;
  omitidos: number;
  errores: { fila: number; mensaje: string }[];
}

export interface ImportJob {
  jobId: string;
  estado: 'EN_CURSO' | 'COMPLETADO' | 'ERROR';
  total: number;
  procesados: number;
  importados: number;
  omitidos: number;
  porcentaje: number;
  errores: { fila: number; mensaje: string }[];
  errorGeneral: string | null;
}

export interface Usuario {
  id: number;
  username: string;
  nombre: string;
  email: string | null;
  activo: boolean;
  roles: string[];
}

export interface CompraLinea {
  id: number;
  codigo: string;
  descripcion: string;
  cantidad: number;
  productoId: number | null;
  productoDescripcion: string | null;
  productoMarca: string | null;
}

export interface Compra {
  id: number;
  numeroFactura: string;
  fechaFactura: string;
  proveedor: string | null;
  estado: 'EN_TRANSITO' | 'INGRESADA';
  ubicacionIngresoId: number | null;
  ubicacionIngresoCodigo: string | null;
  totalLineas: number;
  totalUnidades: number;
  lineasMatcheadas: number;
  createdAt: string;
  lineas: CompraLinea[];
}
