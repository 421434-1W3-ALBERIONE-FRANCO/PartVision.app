import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:provider/provider.dart';

import '../models/ai_extraction.dart';
import '../models/producto.dart';
import '../services/ai_service.dart';
import '../services/api_client.dart';

class AiCaptureScreen extends StatefulWidget {
  const AiCaptureScreen({super.key});

  @override
  State<AiCaptureScreen> createState() => _AiCaptureScreenState();
}

class _AiCaptureScreenState extends State<AiCaptureScreen> {
  final _descripcion = TextEditingController();
  final _sku = TextEditingController();
  final _codigo = TextEditingController();
  final _cantidad = TextEditingController();
  final _ubicacion = TextEditingController();

  bool _procesando = false;
  String? _error;
  AiExtraction? _extraccion;

  @override
  void dispose() {
    _descripcion.dispose();
    _sku.dispose();
    _codigo.dispose();
    _cantidad.dispose();
    _ubicacion.dispose();
    super.dispose();
  }

  Future<void> _tomarFoto() async {
    // maxWidth + imageQuality achican la foto: sube mas rapido, mas barata para
    // la IA y evita el limite de upload; el texto de la etiqueta sigue legible.
    final XFile? foto = await ImagePicker().pickImage(
      source: ImageSource.camera,
      imageQuality: 85,
      maxWidth: 1600,
    );
    if (foto == null) return;
    setState(() {
      _procesando = true;
      _error = null;
    });
    try {
      final bytes = await foto.readAsBytes();
      final extraccion = await context.read<AiService>().extraer(
            bytes,
            foto.name,
            foto.mimeType ?? 'image/jpeg',
          );
      setState(() {
        _extraccion = extraccion;
        _descripcion.text = extraccion.sugeridoDescripcion ?? '';
        _sku.text = extraccion.sugeridoCodigoPieza ?? '';
        _codigo.text = extraccion.sugeridoCodigoBarras ?? '';
      });
    } on ApiException catch (e) {
      setState(() => _error = e.message);
    } catch (_) {
      setState(() => _error = 'No se pudo procesar la imagen');
    } finally {
      if (mounted) setState(() => _procesando = false);
    }
  }

  Future<void> _confirmar() async {
    if (_descripcion.text.trim().isEmpty) {
      setState(() => _error = 'La descripción es obligatoria');
      return;
    }
    final cantidad = int.tryParse(_cantidad.text.trim());
    final ubicacionId = int.tryParse(_ubicacion.text.trim());
    if ((cantidad == null) != (ubicacionId == null)) {
      setState(() => _error = 'Cargá cantidad y ubicación juntas, o ninguna');
      return;
    }
    setState(() {
      _procesando = true;
      _error = null;
    });
    try {
      final codigo = _codigo.text.trim();
      final Producto producto = await context.read<AiService>().confirmar(
            extraccionId: _extraccion!.id,
            descripcion: _descripcion.text.trim(),
            sku: _sku.text.trim(),
            codigos: codigo.isEmpty ? const [] : [ProductoCodigo(codigo: codigo)],
            cantidad: cantidad,
            ubicacionId: ubicacionId,
          );
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Producto confirmado (#${producto.id})')),
      );
      Navigator.of(context).pop(producto);
    } on ApiException catch (e) {
      setState(() => _error = e.message);
    } catch (_) {
      setState(() => _error = 'No se pudo confirmar');
    } finally {
      if (mounted) setState(() => _procesando = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Captura con IA')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: _extraccion == null ? _vistaCaptura() : _vistaRevision(),
      ),
    );
  }

  Widget _vistaCaptura() {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Text('Sacá una foto de la caja/etiqueta.\nLa IA sugiere los datos y vos los revisás.',
              textAlign: TextAlign.center),
          const SizedBox(height: 24),
          if (_procesando)
            const CircularProgressIndicator()
          else
            FilledButton.icon(
              icon: const Icon(Icons.photo_camera),
              label: const Text('Tomar foto'),
              onPressed: _tomarFoto,
            ),
          if (_error != null)
            Padding(padding: const EdgeInsets.only(top: 16), child: Text(_error!, style: const TextStyle(color: Colors.red))),
        ],
      ),
    );
  }

  Widget _vistaRevision() {
    return ListView(
      children: [
        Text('Revisá y corregí lo detectado (modelo: ${_extraccion!.modelo}).',
            style: const TextStyle(fontStyle: FontStyle.italic)),
        const SizedBox(height: 12),
        TextField(controller: _descripcion, decoration: const InputDecoration(labelText: 'Descripción *', border: OutlineInputBorder())),
        const SizedBox(height: 12),
        TextField(controller: _sku, decoration: const InputDecoration(labelText: 'SKU / código de pieza', border: OutlineInputBorder())),
        const SizedBox(height: 12),
        TextField(controller: _codigo, decoration: const InputDecoration(labelText: 'Código de barras', border: OutlineInputBorder())),
        const Divider(height: 32),
        const Text('Stock inicial (opcional)', style: TextStyle(fontWeight: FontWeight.bold)),
        const SizedBox(height: 8),
        TextField(controller: _cantidad, keyboardType: TextInputType.number, decoration: const InputDecoration(labelText: 'Cantidad', border: OutlineInputBorder())),
        const SizedBox(height: 12),
        TextField(controller: _ubicacion, keyboardType: TextInputType.number, decoration: const InputDecoration(labelText: 'Id de ubicación', border: OutlineInputBorder())),
        const SizedBox(height: 20),
        if (_error != null)
          Padding(padding: const EdgeInsets.only(bottom: 12), child: Text(_error!, style: const TextStyle(color: Colors.red))),
        FilledButton(
          onPressed: _procesando ? null : _confirmar,
          child: _procesando
              ? const SizedBox(height: 20, width: 20, child: CircularProgressIndicator(strokeWidth: 2))
              : const Text('Confirmar y crear producto'),
        ),
      ],
    );
  }
}
