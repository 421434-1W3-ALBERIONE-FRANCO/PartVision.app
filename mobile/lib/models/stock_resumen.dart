class StockLinea {
  final int ubicacionId;
  final String ubicacionPath;
  final int cantidad;

  StockLinea({required this.ubicacionId, required this.ubicacionPath, required this.cantidad});

  factory StockLinea.fromJson(Map<String, dynamic> json) => StockLinea(
        ubicacionId: (json['ubicacionId'] as num).toInt(),
        ubicacionPath: json['ubicacionPath'] as String,
        cantidad: (json['cantidad'] as num).toInt(),
      );
}

class StockResumen {
  final int productoId;
  final int total;
  final List<StockLinea> ubicaciones;

  StockResumen({required this.productoId, required this.total, required this.ubicaciones});

  factory StockResumen.fromJson(Map<String, dynamic> json) => StockResumen(
        productoId: (json['productoId'] as num).toInt(),
        total: (json['total'] as num).toInt(),
        ubicaciones: ((json['ubicaciones'] as List<dynamic>?) ?? [])
            .map((e) => StockLinea.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
}
