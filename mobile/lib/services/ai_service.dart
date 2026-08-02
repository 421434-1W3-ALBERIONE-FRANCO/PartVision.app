import '../models/ai_extraction.dart';
import '../models/producto.dart';
import 'api_client.dart';

class AiService {
  final ApiClient api;

  AiService(this.api);

  /// Sube la imagen y obtiene el borrador (PENDIENTE) con los datos sugeridos.
  Future<AiExtraction> extraer(List<int> bytes, String filename, String contentType) async {
    final json = await api.uploadImagen('/extracciones', bytes, filename, contentType);
    return AiExtraction.fromJson(json as Map<String, dynamic>);
  }

  /// Confirma la extraccion con los datos revisados; crea el producto (y stock inicial opcional).
  Future<Producto> confirmar({
    required int extraccionId,
    String? sku,
    int? marcaId,
    required String descripcion,
    List<ProductoCodigo> codigos = const [],
    int? ubicacionId,
    int? cantidad,
  }) async {
    final json = await api.post('/extracciones/$extraccionId/confirmar', {
      'producto': {
        if (sku != null && sku.isNotEmpty) 'sku': sku,
        if (marcaId != null) 'marcaId': marcaId,
        'descripcion': descripcion,
        if (codigos.isNotEmpty) 'codigos': codigos.map((c) => c.toJson()).toList(),
      },
      if (ubicacionId != null) 'ubicacionId': ubicacionId,
      if (cantidad != null) 'cantidad': cantidad,
    });
    return Producto.fromJson((json as Map<String, dynamic>)['producto'] as Map<String, dynamic>);
  }
}
