/// Configuracion centralizada del backend. No hardcodear URLs en los services.
class ApiConfig {
  /// URL base del backend. Override en runtime con:
  /// flutter run --dart-define=API_BASE_URL=http://<ip>:8080/api/v1
  /// El default 10.0.2.2 es el localhost del host visto desde el emulador Android.
  static const String baseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://10.0.2.2:8080/api/v1',
  );
}
