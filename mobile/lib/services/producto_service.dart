import '../models/producto.dart';
import 'api_client.dart';

class ProductoService {
  final ApiClient api;

  ProductoService(this.api);

  /// Busca un producto por codigo (flujo de escaneo). Lanza ApiException 404 si no existe.
  Future<Producto> buscarPorCodigo(String codigo) async {
    final json = await api.get('/productos/buscar?codigo=${Uri.encodeQueryComponent(codigo)}');
    return Producto.fromJson(json as Map<String, dynamic>);
  }

  Future<Producto> crear({
    String? sku,
    int? marcaId,
    int? categoriaId,
    required String descripcion,
    List<ProductoCodigo> codigos = const [],
  }) async {
    final json = await api.post('/productos', {
      if (sku != null && sku.isNotEmpty) 'sku': sku,
      if (marcaId != null) 'marcaId': marcaId,
      if (categoriaId != null) 'categoriaId': categoriaId,
      'descripcion': descripcion,
      if (codigos.isNotEmpty) 'codigos': codigos.map((c) => c.toJson()).toList(),
    });
    return Producto.fromJson(json as Map<String, dynamic>);
  }
}
