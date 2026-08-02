# PartVision — App mobile (Flutter)

App para operarios: **escanear código**, **foto con IA**, **carga manual** y **buscar producto**.
Consume el backend real (Spring Boot) con JWT. Sin datos mockeados.

> ⚠️ Esta app se escribió sin un SDK de Flutter disponible en la máquina de desarrollo,
> por lo que **todavía no fue compilada ni ejecutada**. Seguí los pasos de setup para
> generar el scaffold de plataforma, compilar y correr; los ajustes finos que aparezcan
> se corrigen sobre esta base.

## Setup

Este repo versiona solo `pubspec.yaml` y `lib/` (no las carpetas de plataforma `android/`,
que se generan localmente). Para dejarla ejecutable:

```bash
cd mobile
flutter create . --platforms=android --org com.partvision --project-name partvision_mobile
git checkout -- pubspec.yaml lib/   # flutter create pisa estos; restauramos los nuestros
flutter pub get
flutter run
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
