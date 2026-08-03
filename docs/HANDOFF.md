# PartVision — Contexto de Handoff para agente continuador

> Documento autocontenido. Si sos un agente nuevo tomando este proyecto: leé esto entero antes de tocar nada. Fecha de corte: **2026-08-02**.

---

## 0. TL;DR

PartVision es una app de **digitalización de inventario para un depósito de autopartes**. Arquitectura de 3 capas (backend Spring Boot + web Angular + mobile Flutter) ya escrita en 9 fases. **Todo el código existe; casi nada se ejecutó de forma integrada.** Lo único verificado en runtime hasta ahora: la app Flutter compila, bootea y renderiza el login (smoke test en web). **El próximo objetivo es levantar el stack completo integrado y verificar los flujos reales**, empezando por backend + base de datos.

Regla de producto innegociable: **la IA es solo un borrador. Siempre hay revisión humana antes de confirmar datos.**

---

## 1. Ubicación y entorno

- **Repo raíz:** `C:\Users\alber\Desktop\trabajo\PartVision\PartVision.app`
- **OS:** Windows 11. Shell primario PowerShell; también hay bash (Git Bash) disponible.
- **Git:** branch `main`, working tree limpio salvo carpetas de plataforma Flutter regeneradas (untracked, ver §5).
- **Subcarpetas:** `backend/` (Spring Boot), `web/` (Angular), `mobile/` (Flutter), `Rules-Skills/` (convenciones obligatorias), `docker-compose.yml`.

### Herramientas instaladas
- **Java/Maven:** el backend usa wrapper `./mvnw` (no requiere Maven global).
- **Node/Angular:** presumiblemente instalado (web fue scaffoldeada). Verificar `node -v`.
- **Docker:** requerido para DB/MinIO. Verificar `docker --version`.
- **Flutter 3.44.8 / Dart 3.12.2:** instalado en `C:\Users\alber\Desktop\Flutter\flutter\bin` pero **NO está en el PATH**. En cada comando PowerShell prefijá:
  ```powershell
  $env:Path = "C:\Users\alber\Desktop\Flutter\flutter\bin;" + $env:Path
  ```
  `flutter doctor`: OK para web (Chrome/Edge) y Windows desktop. **Android toolchain AUSENTE (sin Android SDK).**

---

## 2. Historial (fases) — `git log`

| Commit | Fase | Contenido |
|---|---|---|
| `e1de3b5` | — | Initial commit |
| `390b7d3` | 1–5 | MVP backend: auth (JWT+roles), catalog, inventory (stock/movimientos), location, common (auditoría, errores, OpenAPI) |
| `2eff54a` | 6 | Importación masiva CSV de productos + remap puerto DB a 5442 |
| `6578c9d` | 7 | Extracción IA con revisión humana + storage local |
| `90b3059` | 8 | App Flutter para operarios (**marcada "sin verificar"**) |
| `3383d30` | 9 | Panel admin Angular + CORS en backend |
| `0f97036` | 7+ | Proveedor de visión real con SDK oficial de Anthropic |

---

## 3. Arquitectura por capa

### 3.1 Backend (`backend/`) — Spring Boot
Arquitectura por dominio, en capas (controller → service → repository → domain), IoC por constructor, DTOs en los bordes, manejo global de errores.

**Módulos** (`backend/src/main/java/com/partvision/`):
- `auth/` — Usuario, Rol, JWT (JwtService, JwtAuthenticationFilter, SecurityConfig), AuthController, UsuarioController.
- `catalog/` — Producto, Marca, Categoria, ProductoCodigo + controllers/services/DTOs.
- `inventory/` — Stock, MovimientoStock (entrada/salida/ajuste/transferencia), StockController, MovimientoController.
- `location/` — Ubicacion (tipos), UbicacionController.
- `imports/` — ImportController + ImportService + ProductoImporter (CSV).
- `ai/` — AiExtraction (domain + estados), AiExtractionController/Service, storage (LocalStorageService), vision (VisionExtractor con impl `StubVisionExtractor` y `ClaudeVisionExtractor`).
- `common/` — auditoría JPA, ApiError/GlobalExceptionHandler, OpenApiConfig, AuthenticatedUser.

**Migraciones Flyway:** `V1__baseline` … `V6__ai_extractions` en `backend/src/main/resources/db/migration/`.

**Tests:** 40 archivos en `backend/src/test`. Convención del proyecto exige **JaCoCo ≥ 95%** (ver `Rules-Skills/`).

### 3.2 Web (`web/`) — Angular
Panel de administración. Estructura en `web/src/app/`: `core/` (services + auth guard/interceptor + models), `features/` (login, productos, stock, ubicaciones, usuarios, importacion, extracciones, layout). Consume el backend con JWT. **Nunca ejecutado en runtime.**

### 3.3 Mobile (`mobile/`) — Flutter
App para operarios: **escanear código, foto con IA, carga manual, buscar producto**. Consume el backend real con JWT, sin mocks.

Estructura `mobile/lib/`:
- `config/api_config.dart` — URL base (ver §6).
- `models/` — espejo de los DTOs del backend.
- `services/` — ApiClient + un service por dominio (auth, producto, stock, ai) + token_store (flutter_secure_storage).
- `state/auth_state.dart` — provider con token en secure storage.
- `screens/` — login, home, scan (mobile_scanner), search, manual, ai_capture (image_picker).
- `widgets/producto_card.dart`.

El repo **solo versiona `pubspec.yaml` + `lib/`**; las carpetas de plataforma (`android/`, `web/`, `windows/`) se regeneran localmente.

---

## 4. Estado de verificación (crítico)

| Componente | ¿Corre end-to-end? | Nota |
|---|---|---|
| Backend arranca + Flyway + Swagger | ❓ No verificado | |
| Backend tests (`./mvnw test`) | ❓ No corridos esta sesión | |
| Web Angular | ❌ Nunca ejecutado | |
| Mobile compila + bootea + renderiza login | ✅ **Verificado (web)** | 0 issues en analyze |
| Mobile formulario + manejo de error de red | ✅ Verificado | |
| Mobile login/búsqueda real contra backend | ❌ No (backend estaba caído) | |
| Mobile cámara/escaneo | ❌ No (sin Android SDK) | |
| Los 3 tiers integrados a la vez | ❌ Nunca | **Mayor riesgo** |

---

## 5. Cómo levantar cada pieza

### Infra (DB + MinIO + Adminer)
```bash
docker compose up -d
```
- PostgreSQL en host `:5442` (DB/usuario/pass = `partvision`).
- MinIO API `:9000`, consola `:9001` (minioadmin/minioadmin).
- Adminer `:8081`.

### Backend
```bash
cd backend && ./mvnw spring-boot:run
```
- Necesita la DB arriba. Copiá `backend/.env.example` → `.env` si hace falta (perfil `dev`, `DB_URL=jdbc:postgresql://localhost:5442/partvision`).
- API base: `http://localhost:8080/api/v1`. Swagger: `http://localhost:8080/swagger-ui.html`.
- Usuario de prueba: **`admin` / `admin1234`**.
- Tests: `cd backend && ./mvnw test`.

### Web (Angular)
```bash
cd web && npm install && npm start
```
- Sirve en `http://localhost:4200` (ya está en la allowlist de CORS del backend).

### Mobile (Flutter) — regenerar plataformas la primera vez
```powershell
$env:Path = "C:\Users\alber\Desktop\Flutter\flutter\bin;" + $env:Path
cd mobile
flutter create . --platforms=android,web,windows --org com.partvision --project-name partvision_mobile
flutter pub get
```
> `flutter create` NO pisa nuestro `lib/`/`pubspec.yaml` (confirmado por git status). Las carpetas `android/ web/ windows/ test/ .metadata analysis_options.yaml` quedan untracked — es esperado (`.gitignore` las excluye por diseño).

Smoke test web (verificado funcionando):
```powershell
flutter run -d web-server --web-port=8090 --web-hostname=127.0.0.1 --dart-define=API_BASE_URL=http://localhost:8080/api/v1
```

---

## 6. Gotchas / trampas conocidas

1. **Flutter no está en el PATH** — prefijar `$env:Path` en cada comando PowerShell (ver §1).
2. **URL del backend en mobile:** default `http://10.0.2.2:8080/api/v1` en `mobile/lib/config/api_config.dart`. El `10.0.2.2` es SOLO para emulador Android. Para **web/desktop usá `localhost:8080`** vía `--dart-define=API_BASE_URL=...`. Para teléfono físico, la IP de la PC.
3. **CORS:** el backend permite solo `http://localhost:4200` (Angular) por default (`security.cors.allowed-origins`, override con env `CORS_ALLOWED_ORIGINS`). **Flutter web en :8090 será bloqueado por CORS.** Para login end-to-end en web: arrancar backend con `CORS_ALLOWED_ORIGINS` incluyendo el origen de Flutter, o correr Flutter web en `:4200`. Android nativo NO tiene CORS.
4. **IA de visión:** default `AI_VISION_PROVIDER=stub` (no llama a nada, devuelve null). Para IA real: `AI_VISION_PROVIDER=claude` + `ANTHROPIC_API_KEY` (API de pago de console.anthropic.com — **la ingresa el humano**, no es Claude Pro). Modelo default `claude-opus-5` (o `claude-haiku-4-5` para abaratar). Probar IA real tiene costo.
5. **Puerto DB 5442** (no 5432) para no chocar con Postgres nativo.
6. **Android SDK ausente** — instalarlo (Android Studio) es requisito para probar el flujo diferencial de operarios (escaneo + foto).

---

## 7. Convenciones obligatorias (de `Rules-Skills/`)

- Arquitectura en capas, inyección por constructor.
- DTOs en los bordes (no exponer entidades).
- Manejo global de errores.
- **Cobertura JaCoCo ≥ 95%.**
- Reglas de JWT específicas.
- Sin datos mockeados en mobile: consume el backend real.
> Leer los archivos en `Rules-Skills/` antes de escribir código backend.

---

## 8. Próximo plan (priorizado)

**Fase A — Stack completo arriba (máxima prioridad):**
1. `docker compose up -d`
2. Arrancar backend, confirmar Flyway migra + Swagger responde.
3. `./mvnw test` (confirmar 95% JaCoCo).

**Fase B — Verificar Web:** `npm install` + `npm start`, login, recorrer CRUD + pantalla de extracciones IA.

**Fase C — Mobile end-to-end:** login/búsqueda/carga manual reales (web con CORS ajustado, o Android). Luego instalar Android SDK y probar escaneo + foto IA.

**Fase D — IA real (opcional, con costo):** `AI_VISION_PROVIDER=claude` + API key, foto → extracción → revisión humana → confirmación.

**Fase E — Pulido:** ajustes que surjan, CI, docs de despliegue.

---

## 9. Primer paso sugerido para el agente continuador

Arrancar por **Fase A**: `docker compose up -d`, luego `cd backend && ./mvnw spring-boot:run`, verificar que arranca y migra, abrir Swagger y probar login con `admin`/`admin1234`. Reportar cualquier fallo antes de seguir. No asumir que compila: nunca se ejecutó el stack integrado.