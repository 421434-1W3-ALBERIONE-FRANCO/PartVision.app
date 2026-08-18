import 'package:flutter/material.dart';

import '../services/api_client.dart';
import '../services/auth_service.dart';
import '../services/token_store.dart';

class ResetPasswordScreen extends StatefulWidget {
  final String token;

  const ResetPasswordScreen({super.key, required this.token});

  @override
  State<ResetPasswordScreen> createState() => _ResetPasswordScreenState();
}

class _ResetPasswordScreenState extends State<ResetPasswordScreen> {
  final _formKey = GlobalKey<FormState>();
  final _password = TextEditingController();
  final _confirmar = TextEditingController();
  bool _cargando = false;
  bool _exito = false;
  String? _error;

  @override
  void dispose() {
    _password.dispose();
    _confirmar.dispose();
    super.dispose();
  }

  Future<void> _resetear() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() {
      _cargando = true;
      _error = null;
    });
    try {
      final authService = AuthService(ApiClient(TokenStore()));
      await authService.resetPassword(widget.token, _password.text);
      setState(() => _exito = true);
    } on ApiException catch (e) {
      setState(() => _error = e.message);
    } catch (_) {
      setState(() => _error = 'No se pudo conectar con el servidor');
    } finally {
      setState(() => _cargando = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Nueva contraseña')),
      body: Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 400),
            child: _exito ? _mensajeExito() : _formulario(),
          ),
        ),
      ),
    );
  }

  Widget _mensajeExito() {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        const Icon(Icons.check_circle, size: 64, color: Colors.green),
        const SizedBox(height: 16),
        const Text(
          'Contraseña actualizada',
          style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
        ),
        const SizedBox(height: 8),
        const Text(
          'Ya podes iniciar sesion con tu nueva contraseña.',
          textAlign: TextAlign.center,
        ),
        const SizedBox(height: 24),
        FilledButton(
          onPressed: () => Navigator.of(context).popUntil((r) => r.isFirst),
          child: const Text('Ir al login'),
        ),
      ],
    );
  }

  Widget _formulario() {
    return Form(
      key: _formKey,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.lock, size: 48, color: Colors.indigo),
          const SizedBox(height: 16),
          const Text('Ingresa tu nueva contraseña', textAlign: TextAlign.center),
          const SizedBox(height: 24),
          TextFormField(
            controller: _password,
            obscureText: true,
            decoration: const InputDecoration(
              labelText: 'Nueva contraseña',
              border: OutlineInputBorder(),
            ),
            validator: (v) {
              if (v == null || v.isEmpty) return 'Ingresa una contraseña';
              if (v.length < 8) return 'Minimo 8 caracteres';
              return null;
            },
          ),
          const SizedBox(height: 16),
          TextFormField(
            controller: _confirmar,
            obscureText: true,
            decoration: const InputDecoration(
              labelText: 'Confirmar contraseña',
              border: OutlineInputBorder(),
            ),
            validator: (v) {
              if (v != _password.text) return 'Las contraseñas no coinciden';
              return null;
            },
            onFieldSubmitted: (_) => _resetear(),
          ),
          const SizedBox(height: 24),
          if (_error != null)
            Padding(
              padding: const EdgeInsets.only(bottom: 12),
              child: Text(_error!,
                  style: const TextStyle(color: Colors.red),
                  textAlign: TextAlign.center),
            ),
          SizedBox(
            width: double.infinity,
            height: 48,
            child: FilledButton(
              onPressed: _cargando ? null : _resetear,
              child: _cargando
                  ? const SizedBox(
                      height: 20,
                      width: 20,
                      child: CircularProgressIndicator(strokeWidth: 2))
                  : const Text('Cambiar contraseña'),
            ),
          ),
        ],
      ),
    );
  }
}
