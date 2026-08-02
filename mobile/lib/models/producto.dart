class ProductoCodigo {
  final int? id;
  final String codigo;
  final String? tipo;

  ProductoCodigo({this.id, required this.codigo, this.tipo});

  factory ProductoCodigo.fromJson(Map<String, dynamic> json) => ProductoCodigo(
        id: (json['id'] as num?)?.toInt(),
        codigo: json['codigo'] as String,
        tipo: json['tipo'] as String?,
      );

  Map<String, dynamic> toJson() => {
        'codigo': codigo,
        if (tipo != null) 'tipo': tipo,
      };
}

class Producto {
  final int id;
  final String? sku;
  final int? marcaId;
  final String? marcaNombre;
  final int? categoriaId;
  final String? categoriaNombre;
  final String descripcion;
  final String estado;
  final Map<String, dynamic> detallesExtra;
  final List<ProductoCodigo> codigos;

  Producto({
    required this.id,
    this.sku,
    this.marcaId,
    this.marcaNombre,
    this.categoriaId,
    this.categoriaNombre,
    required this.descripcion,
    required this.estado,
    required this.detallesExtra,
    required this.codigos,
  });

  factory Producto.fromJson(Map<String, dynamic> json) => Producto(
        id: (json['id'] as num).toInt(),
        sku: json['sku'] as String?,
        marcaId: (json['marcaId'] as num?)?.toInt(),
        marcaNombre: json['marcaNombre'] as String?,
        categoriaId: (json['categoriaId'] as num?)?.toInt(),
        categoriaNombre: json['categoriaNombre'] as String?,
        descripcion: json['descripcion'] as String,
        estado: json['estado'] as String,
        detallesExtra: (json['detallesExtra'] as Map<String, dynamic>?) ?? {},
        codigos: ((json['codigos'] as List<dynamic>?) ?? [])
            .map((e) => ProductoCodigo.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
}
