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
  final _query = TextEditingController();
  bool _cargando = false;
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
              controller: _query,
              textInputAction: TextInputAction.search,
              decoration: const InputDecoration(
                labelText: 'Nombre, marca, código o categoría',
                hintText: 'Ej: bujia, volkswagen, mahle...',
                border: OutlineInputBorder(),
                prefixIcon: Icon(Icons.search),
              ),
              onSubmitted: (_) => _buscar(),
            ),
            const SizedBox(height: 12),
            FilledButton(onPressed: _cargando ? null : _buscar, child: const Text('Buscar')),
            const SizedBox(height: 16),
            Expanded(child: _cuerpo()),
          ],
        ),
      ),
    );
  }

  Widget _cuerpo() {
    if (_cargando) return const Center(child: CircularProgressIndicator());
    if (_error != null) {
      return Center(child: Text(_error!, style: const TextStyle(color: Colors.red)));
    }
    final res = _resultados;
    if (res == null) {
      return const Center(
        child: Text('Buscá por nombre, marca, código o categoría.',
            style: TextStyle(color: Colors.grey)),
      );
    }
    if (res.isEmpty) {
      return const Center(child: Text('No se encontraron productos.'));
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Padding(
          padding: const EdgeInsets.only(bottom: 8),
          child: Text(
            res.length >= 50 ? 'Mostrando los primeros 50 resultados' : '${res.length} resultado(s)',
            style: const TextStyle(color: Colors.grey, fontSize: 12),
          ),
        ),
        Expanded(
          child: ListView.builder(
            itemCount: res.length,
            itemBuilder: (_, i) => ProductoCard(producto: res[i]),
          ),
        ),
      ],
    );
  }
}
