// Smoke test: con la app recién iniciada y sin token guardado, se muestra el login.
//
// flutter_secure_storage no tiene plugin nativo en el entorno de test, así que
// mockeamos su MethodChannel para que `read` devuelva null (sin sesión previa).

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:partvision_mobile/main.dart';
import 'package:partvision_mobile/services/api_client.dart';
import 'package:partvision_mobile/services/token_store.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const secureStorageChannel =
      MethodChannel('plugins.it_nomads.com/flutter_secure_storage');

  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(secureStorageChannel, (call) async {
      if (call.method == 'readAll') return <String, String>{};
      // read / write / delete / containsKey: sin sesión persistida.
      return null;
    });
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(secureStorageChannel, null);
  });

  testWidgets('arranca en la pantalla de login cuando no hay sesión',
      (WidgetTester tester) async {
    final tokenStore = TokenStore();
    final apiClient = ApiClient(tokenStore);

    await tester.pumpWidget(
      PartVisionApp(apiClient: apiClient, tokenStore: tokenStore),
    );
    await tester.pumpAndSettle();

    // La pantalla de login muestra el título y el botón de ingreso.
    expect(find.text('PartVision'), findsOneWidget);
    expect(find.text('Ingresar'), findsOneWidget);
  });
}
