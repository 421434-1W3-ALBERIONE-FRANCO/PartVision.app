import '../models/movimiento.dart';
import '../models/stock_resumen.dart';
import 'api_client.dart';

class StockService {
  final ApiClient api;

  StockService(this.api);

  Future<StockResumen> resumen(int productoId) async {
    final json = await api.get('/stock?productoId=$productoId');
    return StockResumen.fromJson(json as Map<String, dynamic>);
  }

  Future<Movimiento> registrarEntrada({
    required int productoId,
    required int ubicacionId,
    required int cantidad,
    String? motivo,
  }) async {
    final json = await api.post('/stock/entradas', {
      'productoId': productoId,
      'ubicacionId': ubicacionId,
      'cantidad': cantidad,
      if (motivo != null && motivo.isNotEmpty) 'motivo': motivo,
    });
    return Movimiento.fromJson(json as Map<String, dynamic>);
  }
}
