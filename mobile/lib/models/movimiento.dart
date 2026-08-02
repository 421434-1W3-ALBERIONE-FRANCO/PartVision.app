class Movimiento {
  final int id;
  final int productoId;
  final String tipo;
  final int cantidad;
  final int? ubicacionOrigenId;
  final int? ubicacionDestinoId;
  final int? usuarioId;
  final String? motivo;
  final String? fecha;

  Movimiento({
    required this.id,
    required this.productoId,
    required this.tipo,
    required this.cantidad,
    this.ubicacionOrigenId,
    this.ubicacionDestinoId,
    this.usuarioId,
    this.motivo,
    this.fecha,
  });

  factory Movimiento.fromJson(Map<String, dynamic> json) => Movimiento(
        id: (json['id'] as num).toInt(),
        productoId: (json['productoId'] as num).toInt(),
        tipo: json['tipo'] as String,
        cantidad: (json['cantidad'] as num).toInt(),
        ubicacionOrigenId: (json['ubicacionOrigenId'] as num?)?.toInt(),
        ubicacionDestinoId: (json['ubicacionDestinoId'] as num?)?.toInt(),
        usuarioId: (json['usuarioId'] as num?)?.toInt(),
        motivo: json['motivo'] as String?,
        fecha: json['fecha'] as String?,
      );
}
