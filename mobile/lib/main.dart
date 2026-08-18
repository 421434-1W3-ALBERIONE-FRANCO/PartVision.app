import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'services/ai_service.dart';
import 'services/api_client.dart';
import 'services/auth_service.dart';
import 'services/producto_service.dart';
import 'services/stock_service.dart';
import 'services/token_store.dart';
import 'state/auth_state.dart';
import 'screens/home_screen.dart';
import 'screens/login_screen.dart';
import 'screens/reset_password_screen.dart';

void main() {
  final tokenStore = TokenStore();
  final apiClient = ApiClient(tokenStore);

  runApp(PartVisionApp(
    apiClient: apiClient,
    tokenStore: tokenStore,
  ));
}

class PartVisionApp extends StatelessWidget {
  final ApiClient apiClient;
  final TokenStore tokenStore;

  const PartVisionApp({super.key, required this.apiClient, required this.tokenStore});

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        Provider<ProductoService>(create: (_) => ProductoService(apiClient)),
        Provider<StockService>(create: (_) => StockService(apiClient)),
        Provider<AiService>(create: (_) => AiService(apiClient)),
        ChangeNotifierProvider<AuthState>(
          create: (_) => AuthState(AuthService(apiClient), tokenStore)..inicializar(),
        ),
      ],
      child: MaterialApp(
        title: 'PartVision',
        theme: ThemeData(
          colorScheme: ColorScheme.fromSeed(seedColor: Colors.indigo),
          useMaterial3: true,
        ),
        onGenerateRoute: (settings) {
          final uri = Uri.parse(settings.name ?? '/');

          if (uri.path == '/reset-password') {
            final token = uri.queryParameters['token'];
            if (token != null && token.isNotEmpty) {
              return MaterialPageRoute(
                builder: (_) => ResetPasswordScreen(token: token),
              );
            }
          }

          return MaterialPageRoute(
            builder: (_) => Consumer<AuthState>(
              builder: (_, auth, __) =>
                  auth.autenticado ? const HomeScreen() : const LoginScreen(),
            ),
          );
        },
      ),
    );
  }
}
