import '../models/login_response.dart';
import 'api_client.dart';

class AuthService {
  final ApiClient api;

  AuthService(this.api);

  Future<LoginResponse> login(String username, String password, {String? code}) async {
    final body = <String, dynamic>{
      'username': username,
      'password': password,
    };
    if (code != null && code.isNotEmpty) {
      body['code'] = code;
    }
    final json = await api.post('/auth/login', body);
    return LoginResponse.fromJson(json as Map<String, dynamic>);
  }

  Future<String> forgotPassword(String email) async {
    final json = await api.post('/auth/forgot-password', {'email': email});
    return (json as Map<String, dynamic>)['message'] as String;
  }

  Future<String> resetPassword(String token, String password) async {
    final json = await api.post('/auth/reset-password', {
      'token': token,
      'password': password,
    });
    return (json as Map<String, dynamic>)['message'] as String;
  }

  Future<String> twoFactorRecoverRequest(String email) async {
    final json = await api.post('/auth/2fa/recover-request', {'email': email});
    return (json as Map<String, dynamic>)['message'] as String;
  }

  Future<String> twoFactorRecoverConfirm(String email, String code) async {
    final json = await api.post('/auth/2fa/recover-confirm', {
      'email': email,
      'code': code,
    });
    return (json as Map<String, dynamic>)['message'] as String;
  }
}
