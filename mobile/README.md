# PartVision — App mobile (Flutter)

App para operarios: **escanear código**, **foto con IA**, **carga manual** y **buscar producto**.
Consume el backend real (Spring Boot) con JWT. Sin datos mockeados.

> Estado: verificada en **web** (Flutter 3.44.8): `flutter analyze` sin issues, compila y
> arranca en la pantalla de login. **Android está pendiente** (falta el SDK); el escaneo de
> códigos y la foto con IA son el flujo diferencial y sólo se prueban en Android/dispositivo.

## Setup

Las carpetas de plataforma (`android/`, `web/`, `windows/`) están versionadas, así que alcanza con:

```bash
cd mobile
flutter pub get
flutter run
```

Si necesitás regenerar el scaffold de plataforma (p. ej. tras cambiar `org`/nombre):

```bash
flutter create . --platforms=android,web,windows --org com.partvision --project-name partvision_mobile
git checkout -- pubspec.yaml lib/   # flutter create pisa estos; restauramos los nuestros
flutter pub get
```

Para un smoke test rápido en el navegador (sin Android), apuntando al backend local:

```bash
flutter run -d chrome --dart-define=API_BASE_URL=http://localhost:8080/api/v1
```

## Configuración del backend

La URL base está centralizada en `lib/config/api_config.dart`.
Por defecto apunta a `http://10.0.2.2:8080/api/v1` (el `10.0.2.2` es cómo el emulador de
Android llega al `localhost` de tu PC). Para un dispositivo físico, cambiala por la IP de tu PC.

Se puede sobreescribir sin tocar código:

```bash
flutter run --dart-define=API_BASE_URL=http://192.168.0.10:8080/api/v1
```

## Permisos (Android)

Para cámara/escaneo, agregá en `android/app/src/main/AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.CAMERA" />
```

Usuario de prueba (backend dev): `admin` / `admin1234`.

## Estructura

```
lib/
├── config/       URL base
├── models/       espejo de los DTOs del backend
├── services/     ApiClient + un service por dominio (auth, producto, stock, ai)
├── state/        AuthState (token en almacenamiento seguro)
└── screens/      login, home, scan, search, manual, ai_capture
```
