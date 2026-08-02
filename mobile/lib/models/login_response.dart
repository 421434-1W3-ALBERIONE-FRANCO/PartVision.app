class LoginResponse {
  final String token;
  final int expiresIn;

  LoginResponse({required this.token, required this.expiresIn});

  factory LoginResponse.fromJson(Map<String, dynamic> json) => LoginResponse(
        token: json['token'] as String,
        expiresIn: (json['expiresIn'] as num).toInt(),
      );
}
