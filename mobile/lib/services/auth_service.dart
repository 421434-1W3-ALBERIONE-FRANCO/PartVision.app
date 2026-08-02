import '../models/login_response.dart';
import 'api_client.dart';

class AuthService {
  final ApiClient api;

  AuthService(this.api);

  Future<LoginResponse> login(String username, String password) async {
    final json = await api.post('/auth/login', {
      'username': username,
      'password': password,
    });
    return LoginResponse.fromJson(json as Map<String, dynamic>);
  }
}
