import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../models/producto.dart';
import '../services/api_client.dart';
import '../services/producto_service.dart';
import '../widgets/producto_card.dart';

/// Asocia un código de barras escaneado a un producto YA existente del catálogo.
/// Uso típico: el catálogo importado del proveedor no trae el EAN; el operario
/// busca el producto por texto/SKU y le vincula el código escaneando la caja.
class AsociarCodigoScreen extends StatefulWidget {
  final String codigo;

  const AsociarCodigoScreen({super.key, required this.codigo});

  @override
  State<AsociarCodigoScreen> createState() => _AsociarCodigoScreenState();
}

class _AsociarCodigoScreenState extends State<AsociarCodigoScreen> {
  final _query = TextEditingController();
  bool _cargando = false;
  bool _asociando = false;
  String? _error;
  List<Producto>? _resultados;

  @override
  void dispose() {
    _query.dispose();
    super.dispose();
  }

  Future<void> _buscar() async {
    final q = _query.text.trim();
    if (q.isEmpty) return;
    FocusScope.of(context).unfocus();
    setState(() {
      _cargando = true;
      _error = null;
      _resultados = null;
    });
    try {
      final res = await context.read<ProductoService>().buscarPorTexto(q);
      setState(() => _resultados = res);
    } on ApiException catch (e) {
      setState(() => _error = e.message);
    } catch (_) {
      setState(() => _error = 'No se pudo conectar con el servidor');
    } finally {
      if (mounted) setState(() => _cargando = false);
    }
  }

  Future<void> _asociar(Producto producto) async {
    final confirmar = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Asociar código'),
        content: Text(
            'Asociar el código "${widget.codigo}" a:\n\n${producto.descripcion}'
            '${producto.sku != null ? '\nSKU: ${producto.sku}' : ''}'),
        actions: [
          TextButton(onPressed: () => Navigator.of(ctx).pop(false), child: const Text('Cancelar')),
          FilledButton(onPressed: () => Navigator.of(ctx).pop(true), child: const Text('Asociar')),
        ],
      ),
    );
    if (confirmar != true || !mounted) return;

    setState(() {
      _asociando = true;
      _error = null;
    });
    try {
      await context.read<ProductoService>().agregarCodigo(producto.id, widget.codigo);
      if (!mounted) return;
      // Devuelve true para que el escáner vuelva a buscar y muestre el producto ya vinculado.
      Navigator.of(context).pop(true);
    } on ApiException catch (e) {
      setState(() => _error = e.message);
    } catch (_) {
      setState(() => _error = 'No se pudo asociar el código');
    } finally {
      if (mounted) setState(() => _asociando = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Asociar código')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: Theme.of(context).colorScheme.surfaceContainerHighest,
                borderRadius: BorderRadius.circular(8),
              ),
              child: Row(
                children: [
                  const Icon(Icons.qr_code_2),
                  const SizedBox(width: 8),
                  Expanded(child: Text('Código escaneado: ${widget.codigo}',
                      style: const TextStyle(fontWeight: FontWeight.bold))),
                ],
              ),
            ),
            const SizedBox(height: 12),
            const Text('Buscá el producto del catálogo al que pertenece:'),
            const SizedBox(height: 8),
            TextField(
              controller: _query,
              textInputAction: TextInputAction.search,
              decoration: const InputDecoration(
                labelText: 'Nombre, marca, SKU o categoría',
                border: OutlineInputBorder(),
                prefixIcon: Icon(Icons.search),
              ),
              onSubmitted: (_) => _buscar(),
            ),
            const SizedBox(height: 12),
            FilledButton(onPressed: _cargando ? null : _buscar, child: const Text('Buscar')),
            if (_error != null)
              Padding(
                padding: const EdgeInsets.only(top: 12),
                child: Text(_error!, style: const TextStyle(color: Colors.red)),
              ),
            const SizedBox(height: 12),
            Expanded(child: _cuerpo()),
          ],
        ),
      ),
    );
  }

  Widget _cuerpo() {
    if (_asociando) return const Center(child: CircularProgressIndicator());
    if (_cargando) return const Center(child: CircularProgressIndicator());
    final res = _resultados;
    if (res == null) {
      return const Center(
        child: Text('Buscá y tocá el producto para asociarle el código.',
            style: TextStyle(color: Colors.grey)),
      );
    }
    if (res.isEmpty) {
      return const Center(child: Text('No se encontraron productos.'));
    }
    return ListView.builder(
      itemCount: res.length,
      itemBuilder: (_, i) => ProductoCard(
        producto: res[i],
        onTap: () => _asociar(res[i]),
      ),
    );
  }
}
