import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:http_parser/http_parser.dart';

import '../config/api_config.dart';
import 'token_store.dart';

/// Error de API con el codigo HTTP y el mensaje del backend (ApiError.message).
class ApiException implements Exception {
  final int statusCode;
  final String message;
  final Map<String, dynamic>? data;

  ApiException(this.statusCode, this.message, {this.data});

  bool get twoFactorRequired => data?['twoFactorRequired'] == true;

  @override
  String toString() => message;
}

/// Cliente HTTP centralizado: agrega el JWT, decodifica JSON y traduce errores.
class ApiClient {
  final TokenStore tokenStore;
  final http.Client _http;

  ApiClient(this.tokenStore, [http.Client? client]) : _http = client ?? http.Client();

  Uri _uri(String path) => Uri.parse('${ApiConfig.baseUrl}$path');

  Future<Map<String, String>> _headers({bool json = true}) async {
    final token = await tokenStore.read();
    return {
      if (json) 'Content-Type': 'application/json',
      if (token != null) 'Authorization': 'Bearer $token',
    };
  }

  Future<dynamic> get(String path) async {
    final res = await _http.get(_uri(path), headers: await _headers(json: false));
    return _procesar(res);
  }

  Future<dynamic> post(String path, Object body) async {
    final res = await _http.post(_uri(path), headers: await _headers(), body: jsonEncode(body));
    return _procesar(res);
  }

  Future<dynamic> uploadImagen(String path, List<int> bytes, String filename, String contentType) async {
    final request = http.MultipartRequest('POST', _uri(path));
    request.headers.addAll(await _headers(json: false));
    request.files.add(http.MultipartFile.fromBytes(
      'archivo',
      bytes,
      filename: filename,
      contentType: MediaType.parse(contentType),
    ));
    final res = await http.Response.fromStream(await request.send());
    return _procesar(res);
  }

  dynamic _procesar(http.Response res) {
    final cuerpo = res.body.isEmpty ? null : jsonDecode(utf8.decode(res.bodyBytes));
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return cuerpo;
    }
    final mensaje = (cuerpo is Map && cuerpo['message'] != null)
        ? cuerpo['message'].toString()
        : 'Error ${res.statusCode}';
    throw ApiException(res.statusCode, mensaje,
        data: cuerpo is Map<String, dynamic> ? cuerpo : null);
  }
}
