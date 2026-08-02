import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../state/auth_state.dart';
import 'ai_capture_screen.dart';
import 'manual_screen.dart';
import 'scan_screen.dart';
import 'search_screen.dart';

class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('PartVision'),
        actions: [
          IconButton(
            tooltip: 'Salir',
            icon: const Icon(Icons.logout),
            onPressed: () => context.read<AuthState>().logout(),
          ),
        ],
      ),
      body: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            _Accion(
              icono: Icons.qr_code_scanner,
              texto: 'Escanear código',
              destino: () => const ScanScreen(),
            ),
            _Accion(
              icono: Icons.photo_camera,
              texto: 'Sacar foto con IA',
              destino: () => const AiCaptureScreen(),
            ),
            _Accion(
              icono: Icons.edit_note,
              texto: 'Carga manual',
              destino: () => const ManualScreen(),
            ),
            _Accion(
              icono: Icons.search,
              texto: 'Buscar producto',
              destino: () => const SearchScreen(),
            ),
          ],
        ),
      ),
    );
  }
}

class _Accion extends StatelessWidget {
  final IconData icono;
  final String texto;
  final Widget Function() destino;

  const _Accion({required this.icono, required this.texto, required this.destino});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: SizedBox(
        width: double.infinity,
        height: 72,
        child: FilledButton.icon(
          icon: Icon(icono, size: 28),
          label: Text(texto, style: const TextStyle(fontSize: 18)),
          onPressed: () => Navigator.of(context).push(
            MaterialPageRoute(builder: (_) => destino()),
          ),
        ),
      ),
    );
  }
}
