import '../models/producto.dart';
import 'api_client.dart';

class ProductoService {
  final ApiClient api;

  ProductoService(this.api);

  /// Busca un producto por codigo exacto (flujo de escaneo). Lanza ApiException 404 si no existe.
  Future<Producto> buscarPorCodigo(String codigo) async {
    final json = await api.get('/productos/buscar?codigo=${Uri.encodeQueryComponent(codigo)}');
    return Producto.fromJson(json as Map<String, dynamic>);
  }

  /// Asocia un codigo (ej: codigo de barras escaneado) a un producto existente.
  Future<Producto> agregarCodigo(int productoId, String codigo, {String tipo = 'BARRA'}) async {
    final json = await api.post('/productos/$productoId/codigos', {
      'codigo': codigo,
      'tipo': tipo,
    });
    return Producto.fromJson(json as Map<String, dynamic>);
  }

  /// Busqueda por texto parcial (nombre, marca, SKU, categoria). Devuelve una lista.
  Future<List<Producto>> buscarPorTexto(String q, {int size = 50}) async {
    final json = await api.get(
        '/productos/buscar-texto?q=${Uri.encodeQueryComponent(q)}&size=$size');
    final content = (json as Map<String, dynamic>)['content'] as List<dynamic>? ?? [];
    return content.map((e) => Producto.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<Producto> crear({
    String? sku,
    int? marcaId,
    String? marcaNombre,
    int? categoriaId,
    required String descripcion,
    List<ProductoCodigo> codigos = const [],
  }) async {
    final json = await api.post('/productos', {
      if (sku != null && sku.isNotEmpty) 'sku': sku,
      if (marcaId != null) 'marcaId': marcaId,
      if (marcaNombre != null && marcaNombre.isNotEmpty) 'marcaNombre': marcaNombre,
      if (categoriaId != null) 'categoriaId': categoriaId,
      'descripcion': descripcion,
      if (codigos.isNotEmpty) 'codigos': codigos.map((c) => c.toJson()).toList(),
    });
    return Producto.fromJson(json as Map<String, dynamic>);
  }
}
