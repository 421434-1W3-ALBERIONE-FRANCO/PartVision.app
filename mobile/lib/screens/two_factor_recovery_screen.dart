import 'package:flutter/material.dart';

import '../services/api_client.dart';
import '../services/auth_service.dart';
import '../services/token_store.dart';

class TwoFactorRecoveryScreen extends StatefulWidget {
  const TwoFactorRecoveryScreen({super.key});

  @override
  State<TwoFactorRecoveryScreen> createState() => _TwoFactorRecoveryScreenState();
}

class _TwoFactorRecoveryScreenState extends State<TwoFactorRecoveryScreen> {
  final _emailKey = GlobalKey<FormState>();
  final _codeKey = GlobalKey<FormState>();
  final _email = TextEditingController();
  final _codigo = TextEditingController();
  bool _cargando = false;
  bool _emailEnviado = false;
  bool _exito = false;
  String? _error;

  late final AuthService _authService;

  @override
  void initState() {
    super.initState();
    _authService = AuthService(ApiClient(TokenStore()));
  }

  @override
  void dispose() {
    _email.dispose();
    _codigo.dispose();
    super.dispose();
  }

  Future<void> _enviarEmail() async {
    if (!_emailKey.currentState!.validate()) return;
    setState(() {
      _cargando = true;
      _error = null;
    });
    try {
      await _authService.twoFactorRecoverRequest(_email.text.trim());
      setState(() => _emailEnviado = true);
    } on ApiException catch (e) {
      setState(() => _error = e.message);
    } catch (_) {
      setState(() => _error = 'No se pudo conectar con el servidor');
    } finally {
      setState(() => _cargando = false);
    }
  }

  Future<void> _confirmarCodigo() async {
    if (!_codeKey.currentState!.validate()) return;
    setState(() {
      _cargando = true;
      _error = null;
    });
    try {
      await _authService.twoFactorRecoverConfirm(
          _email.text.trim(), _codigo.text.trim());
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
      appBar: AppBar(title: const Text('Recuperar 2FA')),
      body: Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 400),
            child: _exito
                ? _mensajeExito()
                : _emailEnviado
                    ? _formularioCodigo()
                    : _formularioEmail(),
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
          '2FA desactivado',
          style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
        ),
        const SizedBox(height: 8),
        const Text(
          'Ya podes iniciar sesion normalmente. Si queres, podes volver a configurar 2FA desde tu cuenta.',
          textAlign: TextAlign.center,
          style: TextStyle(color: Colors.grey),
        ),
        const SizedBox(height: 24),
        FilledButton(
          onPressed: () => Navigator.of(context).popUntil((r) => r.isFirst),
          child: const Text('Ir al login'),
        ),
      ],
    );
  }

  Widget _formularioEmail() {
    return Form(
      key: _emailKey,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.security, size: 48, color: Colors.indigo),
          const SizedBox(height: 16),
          const Text(
            'Ingresa tu email para recibir un codigo de recuperacion',
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: 24),
          TextFormField(
            controller: _email,
            keyboardType: TextInputType.emailAddress,
            decoration: const InputDecoration(
              labelText: 'Email',
              border: OutlineInputBorder(),
              prefixIcon: Icon(Icons.email),
            ),
            validator: (v) {
              if (v == null || v.trim().isEmpty) return 'Ingresa tu email';
              if (!v.contains('@')) return 'Email invalido';
              return null;
            },
            onFieldSubmitted: (_) => _enviarEmail(),
          ),
          const SizedBox(height: 24),
          if (_error != null)
            Padding(
              padding: const EdgeInsets.only(bottom: 12),
              child: Text(_error!, style: const TextStyle(color: Colors.red)),
            ),
          SizedBox(
            width: double.infinity,
            height: 48,
            child: FilledButton(
              onPressed: _cargando ? null : _enviarEmail,
              child: _cargando
                  ? const SizedBox(
                      height: 20,
                      width: 20,
                      child: CircularProgressIndicator(strokeWidth: 2))
                  : const Text('Enviar codigo'),
            ),
          ),
        ],
      ),
    );
  }

  Widget _formularioCodigo() {
    return Form(
      key: _codeKey,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.mark_email_read, size: 48, color: Colors.green),
          const SizedBox(height: 16),
          const Text(
            'Ingresa el codigo de 6 digitos que recibiste por email',
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: 24),
          TextFormField(
            controller: _codigo,
            keyboardType: TextInputType.number,
            maxLength: 6,
            textAlign: TextAlign.center,
            style: const TextStyle(fontSize: 32, letterSpacing: 12),
            decoration: const InputDecoration(
              labelText: 'Codigo',
              border: OutlineInputBorder(),
              counterText: '',
            ),
            validator: (v) {
              if (v == null || v.trim().isEmpty) return 'Ingresa el codigo';
              if (v.trim().length != 6) return 'El codigo debe tener 6 digitos';
              return null;
            },
            onFieldSubmitted: (_) => _confirmarCodigo(),
            autofocus: true,
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
              onPressed: _cargando ? null : _confirmarCodigo,
              child: _cargando
                  ? const SizedBox(
                      height: 20,
                      width: 20,
                      child: CircularProgressIndicator(strokeWidth: 2))
                  : const Text('Verificar'),
            ),
          ),
          const SizedBox(height: 12),
          TextButton(
            onPressed: _cargando ? null : _enviarEmail,
            child: const Text('Reenviar codigo'),
          ),
        ],
      ),
    );
  }
}
