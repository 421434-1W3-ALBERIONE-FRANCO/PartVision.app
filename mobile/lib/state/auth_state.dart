import 'package:flutter/foundation.dart';

import '../services/api_client.dart';
import '../services/auth_service.dart';
import '../services/token_store.dart';

class AuthState extends ChangeNotifier {
  final AuthService _authService;
  final TokenStore _tokenStore;

  String? _token;
  bool _cargando = false;
  String? _error;

  AuthState(this._authService, this._tokenStore);

  bool get autenticado => _token != null;
  bool get cargando => _cargando;
  String? get error => _error;

  /// Carga el token persistido al arrancar la app.
  Future<void> inicializar() async {
    _token = await _tokenStore.read();
    notifyListeners();
  }

  Future<bool> login(String username, String password) async {
    _cargando = true;
    _error = null;
    notifyListeners();
    try {
      final res = await _authService.login(username, password);
      await _tokenStore.save(res.token);
      _token = res.token;
      return true;
    } on ApiException catch (e) {
      _error = e.statusCode == 401 ? 'Usuario o contraseña incorrectos' : e.message;
      return false;
    } catch (_) {
      _error = 'No se pudo conectar con el servidor';
      return false;
    } finally {
      _cargando = false;
      notifyListeners();
    }
  }

  Future<void> logout() async {
    await _tokenStore.clear();
    _token = null;
    notifyListeners();
  }
}
