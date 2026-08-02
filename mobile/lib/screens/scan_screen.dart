import 'package:flutter/material.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import 'package:provider/provider.dart';

import '../models/producto.dart';
import '../models/stock_resumen.dart';
import '../services/api_client.dart';
import '../services/producto_service.dart';
import '../services/stock_service.dart';
import '../widgets/producto_card.dart';
import 'ai_capture_screen.dart';
import 'manual_screen.dart';

class ScanScreen extends StatefulWidget {
  const ScanScreen({super.key});

  @override
  State<ScanScreen> createState() => _ScanScreenState();
}

class _ScanScreenState extends State<ScanScreen> {
  final MobileScannerController _scanner = MobileScannerController();
  final _cantidad = TextEditingController();
  final _ubicacion = TextEditingController();

  bool _procesando = false;
  bool _cargando = false;
  bool _registrando = false;
  String? _error;
  String? _codigo;
  Producto? _producto;
  StockResumen? _stock;
  bool _noExiste = false;

  @override
  void dispose() {
    _scanner.dispose();
    _cantidad.dispose();
    _ubicacion.dispose();
    super.dispose();
  }

  void _onDetect(BarcodeCapture capture) {
    if (_procesando) return;
    final codigo = capture.barcodes.isNotEmpty ? capture.barcodes.first.rawValue : null;
    if (codigo == null || codigo.isEmpty) return;
    _procesando = true;
    _scanner.stop();
    _buscar(codigo);
  }

  Future<void> _buscar(String codigo) async {
    setState(() {
      _cargando = true;
      _error = null;
      _producto = null;
      _stock = null;
      _noExiste = false;
      _codigo = codigo;
    });
    try {
      final producto = await context.read<ProductoService>().buscarPorCodigo(codigo);
      final stock = await context.read<StockService>().resumen(producto.id);
      setState(() {
        _producto = producto;
        _stock = stock;
      });
    } on ApiException catch (e) {
      if (e.statusCode == 404) {
        setState(() => _noExiste = true);
      } else {
        setState(() => _error = e.message);
      }
    } catch (_) {
      setState(() => _error = 'No se pudo conectar con el servidor');
    } finally {
      setState(() => _cargando = false);
    }
  }

  Future<void> _registrarEntrada() async {
    final cantidad = int.tryParse(_cantidad.text.trim());
    final ubicacionId = int.tryParse(_ubicacion.text.trim());
    if (cantidad == null || cantidad <= 0 || ubicacionId == null) {
      setState(() => _error = 'Ingresá una cantidad válida y el id de ubicación');
      return;
    }
    setState(() {
      _registrando = true;
      _error = null;
    });
    try {
      await context.read<StockService>().registrarEntrada(
            productoId: _producto!.id,
            ubicacionId: ubicacionId,
            cantidad: cantidad,
            motivo: 'Entrada por escaneo',
          );
      final stock = await context.read<StockService>().resumen(_producto!.id);
      if (!mounted) return;
      setState(() => _stock = stock);
      _cantidad.clear();
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Entrada registrada')),
      );
    } on ApiException catch (e) {
      setState(() => _error = e.message);
    } catch (_) {
      setState(() => _error = 'No se pudo conectar con el servidor');
    } finally {
      if (mounted) setState(() => _registrando = false);
    }
  }

  void _reiniciar() {
    setState(() {
      _procesando = false;
      _producto = null;
      _stock = null;
      _noExiste = false;
      _error = null;
      _codigo = null;
    });
    _scanner.start();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Escanear código')),
      body: Column(
        children: [
          SizedBox(
            height: 240,
            child: _procesando
                ? Container(
                    color: Colors.black12,
                    alignment: Alignment.center,
                    child: Text('Código: ${_codigo ?? ''}'),
                  )
                : MobileScanner(controller: _scanner, onDetect: _onDetect),
          ),
          Expanded(
            child: SingleChildScrollView(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  if (_cargando) const Center(child: Padding(padding: EdgeInsets.all(16), child: CircularProgressIndicator())),
                  if (_error != null)
                    Padding(padding: const EdgeInsets.only(bottom: 8), child: Text(_error!, style: const TextStyle(color: Colors.red))),
                  if (_producto != null) ...[
                    ProductoCard(producto: _producto!),
                    const SizedBox(height: 8),
                    if (_stock != null) Text('Stock total: ${_stock!.total}', style: const TextStyle(fontWeight: FontWeight.bold)),
                    for (final l in _stock?.ubicaciones ?? []) Text('  ${l.ubicacionPath}: ${l.cantidad}'),
                    const Divider(height: 24),
                    const Text('Registrar entrada', style: TextStyle(fontWeight: FontWeight.bold)),
                    const SizedBox(height: 8),
                    TextField(
                      controller: _cantidad,
                      keyboardType: TextInputType.number,
                      decoration: const InputDecoration(labelText: 'Cantidad', border: OutlineInputBorder()),
                    ),
                    const SizedBox(height: 8),
                    TextField(
                      controller: _ubicacion,
                      keyboardType: TextInputType.number,
                      decoration: const InputDecoration(labelText: 'Id de ubicación', border: OutlineInputBorder()),
                    ),
                    const SizedBox(height: 12),
                    FilledButton(
                      onPressed: _registrando ? null : _registrarEntrada,
                      child: _registrando
                          ? const SizedBox(height: 20, width: 20, child: CircularProgressIndicator(strokeWidth: 2))
                          : const Text('Registrar entrada'),
                    ),
                  ],
                  if (_noExiste) ...[
                    Text('No existe un producto con el código "$_codigo".'),
                    const SizedBox(height: 12),
                    OutlinedButton.icon(
                      icon: const Icon(Icons.photo_camera),
                      label: const Text('Crear con IA'),
                      onPressed: () => Navigator.of(context).push(
                        MaterialPageRoute(builder: (_) => const AiCaptureScreen()),
                      ),
                    ),
                    const SizedBox(height: 8),
                    OutlinedButton.icon(
                      icon: const Icon(Icons.edit_note),
                      label: const Text('Carga manual'),
                      onPressed: () => Navigator.of(context).push(
                        MaterialPageRoute(builder: (_) => ManualScreen(codigoInicial: _codigo)),
                      ),
                    ),
                  ],
                  if (_procesando) ...[
                    const SizedBox(height: 16),
                    TextButton.icon(
                      icon: const Icon(Icons.qr_code_scanner),
                      label: const Text('Escanear de nuevo'),
                      onPressed: _reiniciar,
                    ),
                  ],
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
