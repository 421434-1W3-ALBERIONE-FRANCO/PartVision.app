import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../models/producto.dart';
import '../services/api_client.dart';
import '../services/producto_service.dart';
import '../widgets/producto_card.dart';

class SearchScreen extends StatefulWidget {
  const SearchScreen({super.key});

  @override
  State<SearchScreen> createState() => _SearchScreenState();
}

class _SearchScreenState extends State<SearchScreen> {
  final _codigo = TextEditingController();
  bool _cargando = false;
  String? _error;
  Producto? _producto;

  @override
  void dispose() {
    _codigo.dispose();
    super.dispose();
  }

  Future<void> _buscar() async {
    final codigo = _codigo.text.trim();
    if (codigo.isEmpty) return;
    setState(() {
      _cargando = true;
      _error = null;
      _producto = null;
    });
    try {
      final p = await context.read<ProductoService>().buscarPorCodigo(codigo);
      setState(() => _producto = p);
    } on ApiException catch (e) {
      setState(() => _error = e.statusCode == 404 ? 'No existe un producto con ese código' : e.message);
    } catch (_) {
      setState(() => _error = 'No se pudo conectar con el servidor');
    } finally {
      setState(() => _cargando = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Buscar producto')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            TextField(
              controller: _codigo,
              decoration: const InputDecoration(
                labelText: 'Código',
                border: OutlineInputBorder(),
                prefixIcon: Icon(Icons.search),
              ),
              onSubmitted: (_) => _buscar(),
            ),
            const SizedBox(height: 12),
            FilledButton(onPressed: _cargando ? null : _buscar, child: const Text('Buscar')),
            const SizedBox(height: 16),
            if (_cargando) const Center(child: CircularProgressIndicator()),
            if (_error != null) Text(_error!, style: const TextStyle(color: Colors.red)),
            if (_producto != null) ProductoCard(producto: _producto!),
          ],
        ),
      ),
    );
  }
}
