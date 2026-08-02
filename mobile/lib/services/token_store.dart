import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Guarda el JWT en almacenamiento seguro del dispositivo.
class TokenStore {
  static const _key = 'jwt_token';
  final FlutterSecureStorage _storage;

  TokenStore([FlutterSecureStorage? storage])
      : _storage = storage ?? const FlutterSecureStorage();

  Future<String?> read() => _storage.read(key: _key);

  Future<void> save(String token) => _storage.write(key: _key, value: token);

  Future<void> clear() => _storage.delete(key: _key);
}
