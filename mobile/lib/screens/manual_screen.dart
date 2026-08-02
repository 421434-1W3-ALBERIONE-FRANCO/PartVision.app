import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../models/producto.dart';
import '../services/api_client.dart';
import '../services/producto_service.dart';

class ManualScreen extends StatefulWidget {
  /// Código precargado (ej: viene del escaneo de un producto inexistente).
  final String? codigoInicial;

  const ManualScreen({super.key, this.codigoInicial});

  @override
  State<ManualScreen> createState() => _ManualScreenState();
}

class _ManualScreenState extends State<ManualScreen> {
  final _formKey = GlobalKey<FormState>();
  final _descripcion = TextEditingController();
  final _sku = TextEditingController();
  late final TextEditingController _codigo;
  bool _guardando = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _codigo = TextEditingController(text: widget.codigoInicial ?? '');
  }

  @override
  void dispose() {
    _descripcion.dispose();
    _sku.dispose();
    _codigo.dispose();
    super.dispose();
  }

  Future<void> _guardar() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() {
      _guardando = true;
      _error = null;
    });
    try {
      final codigo = _codigo.text.trim();
      final producto = await context.read<ProductoService>().crear(
            descripcion: _descripcion.text.trim(),
            sku: _sku.text.trim(),
            codigos: codigo.isEmpty ? const [] : [ProductoCodigo(codigo: codigo)],
          );
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Producto creado (#${producto.id})')),
      );
      Navigator.of(context).pop(producto);
    } on ApiException catch (e) {
      setState(() => _error = e.message);
    } catch (_) {
      setState(() => _error = 'No se pudo conectar con el servidor');
    } finally {
      if (mounted) setState(() => _guardando = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Carga manual')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Form(
          key: _formKey,
          child: ListView(
            children: [
              TextFormField(
                controller: _descripcion,
                decoration: const InputDecoration(labelText: 'Descripción *', border: OutlineInputBorder()),
                validator: (v) => (v == null || v.trim().isEmpty) ? 'La descripción es obligatoria' : null,
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _sku,
                decoration: const InputDecoration(labelText: 'SKU / código de pieza', border: OutlineInputBorder()),
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _codigo,
                decoration: const InputDecoration(labelText: 'Código de barras', border: OutlineInputBorder()),
              ),
              const SizedBox(height: 20),
              if (_error != null)
                Padding(
                  padding: const EdgeInsets.only(bottom: 12),
                  child: Text(_error!, style: const TextStyle(color: Colors.red)),
                ),
              FilledButton(
                onPressed: _guardando ? null : _guardar,
                child: _guardando
                    ? const SizedBox(height: 20, width: 20, child: CircularProgressIndicator(strokeWidth: 2))
                    : const Text('Guardar producto'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
