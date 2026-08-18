import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../state/auth_state.dart';
import 'forgot_password_screen.dart';
import 'two_factor_recovery_screen.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _formKey = GlobalKey<FormState>();
  final _usuario = TextEditingController();
  final _password = TextEditingController();
  final _codigo2fa = TextEditingController();

  @override
  void dispose() {
    _usuario.dispose();
    _password.dispose();
    _codigo2fa.dispose();
    super.dispose();
  }

  Future<void> _login() async {
    if (!_formKey.currentState!.validate()) return;
    final auth = context.read<AuthState>();
    final code = auth.requiere2fa ? _codigo2fa.text.trim() : null;
    await auth.login(_usuario.text.trim(), _password.text, code: code);
  }

  void _volver() {
    context.read<AuthState>().limpiar2fa();
    _codigo2fa.clear();
  }

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthState>();
    final mostrar2fa = auth.requiere2fa;

    return Scaffold(
      body: Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 400),
            child: Form(
              key: _formKey,
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Text('PartVision',
                      style: TextStyle(fontSize: 32, fontWeight: FontWeight.bold)),
                  const SizedBox(height: 32),

                  if (!mostrar2fa) ...[
                    TextFormField(
                      controller: _usuario,
                      decoration: const InputDecoration(
                          labelText: 'Usuario', border: OutlineInputBorder()),
                      validator: (v) =>
                          (v == null || v.trim().isEmpty) ? 'Ingresa el usuario' : null,
                    ),
                    const SizedBox(height: 16),
                    TextFormField(
                      controller: _password,
                      obscureText: true,
                      decoration: const InputDecoration(
                          labelText: 'Contraseña', border: OutlineInputBorder()),
                      validator: (v) =>
                          (v == null || v.isEmpty) ? 'Ingresa la contraseña' : null,
                      onFieldSubmitted: (_) => _login(),
                    ),
                  ],

                  if (mostrar2fa) ...[
                    const Icon(Icons.security, size: 48, color: Colors.indigo),
                    const SizedBox(height: 16),
                    const Text(
                      'Ingresa el codigo de tu autenticador',
                      style: TextStyle(fontSize: 16),
                      textAlign: TextAlign.center,
                    ),
                    const SizedBox(height: 16),
                    TextFormField(
                      controller: _codigo2fa,
                      keyboardType: TextInputType.number,
                      maxLength: 16,
                      textAlign: TextAlign.center,
                      style: const TextStyle(fontSize: 24, letterSpacing: 8),
                      decoration: const InputDecoration(
                        labelText: 'Codigo 2FA',
                        border: OutlineInputBorder(),
                        counterText: '',
                      ),
                      validator: (v) =>
                          (v == null || v.trim().isEmpty) ? 'Ingresa el codigo' : null,
                      onFieldSubmitted: (_) => _login(),
                      autofocus: true,
                    ),
                  ],

                  const SizedBox(height: 24),

                  if (auth.error != null)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 12),
                      child: Text(auth.error!,
                          style: const TextStyle(color: Colors.red),
                          textAlign: TextAlign.center),
                    ),

                  SizedBox(
                    width: double.infinity,
                    height: 48,
                    child: FilledButton(
                      onPressed: auth.cargando ? null : _login,
                      child: auth.cargando
                          ? const SizedBox(
                              height: 20,
                              width: 20,
                              child: CircularProgressIndicator(strokeWidth: 2))
                          : Text(mostrar2fa ? 'Verificar' : 'Ingresar'),
                    ),
                  ),

                  const SizedBox(height: 16),

                  if (!mostrar2fa)
                    TextButton(
                      onPressed: () => Navigator.of(context).push(
                        MaterialPageRoute(builder: (_) => const ForgotPasswordScreen()),
                      ),
                      child: const Text('Olvide mi contraseña'),
                    ),

                  if (mostrar2fa) ...[
                    TextButton(
                      onPressed: () => Navigator.of(context).push(
                        MaterialPageRoute(
                            builder: (_) => const TwoFactorRecoveryScreen()),
                      ),
                      child: const Text('Perdi el acceso a mi autenticador'),
                    ),
                    TextButton(
                      onPressed: _volver,
                      child: const Text('Volver'),
                    ),
                  ],
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
