class AiExtraction {
  final int id;
  final String imagenKey;
  final String modelo;
  final String estado;
  final Map<String, dynamic> datosSugeridos;
  final int? productoId;

  AiExtraction({
    required this.id,
    required this.imagenKey,
    required this.modelo,
    required this.estado,
    required this.datosSugeridos,
    this.productoId,
  });

  factory AiExtraction.fromJson(Map<String, dynamic> json) => AiExtraction(
        id: (json['id'] as num).toInt(),
        imagenKey: json['imagenKey'] as String,
        modelo: json['modelo'] as String,
        estado: json['estado'] as String,
        datosSugeridos: (json['datosSugeridos'] as Map<String, dynamic>?) ?? {},
        productoId: (json['productoId'] as num?)?.toInt(),
      );

  String? get sugeridoDescripcion => datosSugeridos['descripcion'] as String?;
  String? get sugeridoMarca => datosSugeridos['marca'] as String?;
  String? get sugeridoCodigoPieza => datosSugeridos['codigo_pieza'] as String?;
  String? get sugeridoCodigoBarras => datosSugeridos['codigo_barras'] as String?;
}
