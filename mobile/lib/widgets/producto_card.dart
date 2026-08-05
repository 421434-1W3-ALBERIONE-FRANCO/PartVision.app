import 'package:flutter/material.dart';

import '../models/producto.dart';

/// Tarjeta con los datos de un producto. Si se pasa [onTap] es seleccionable
/// (ej: elegir el producto al que asociar un código escaneado).
class ProductoCard extends StatelessWidget {
  final Producto producto;
  final VoidCallback? onTap;

  const ProductoCard({super.key, required this.producto, this.onTap});

  @override
  Widget build(BuildContext context) {
    return Card(
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(producto.descripcion, style: Theme.of(context).textTheme.titleMedium),
              const SizedBox(height: 8),
              if (producto.marcaNombre != null) Text('Marca: ${producto.marcaNombre}'),
              if (producto.sku != null) Text('SKU: ${producto.sku}'),
              if (producto.categoriaNombre != null) Text('Categoría: ${producto.categoriaNombre}'),
              Text('Estado: ${producto.estado}'),
              if (producto.codigos.isNotEmpty)
                Text('Códigos: ${producto.codigos.map((c) => c.codigo).join(', ')}'),
            ],
          ),
        ),
      ),
    );
  }
}
