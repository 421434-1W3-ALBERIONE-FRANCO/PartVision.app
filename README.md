# PartVision — Sistema Integral de Gestión de Autopartes con Visión Computacional IA

PartVision es una plataforma moderna para el control de stock, catálogo de autopartes, ubicaciones físicas de depósito y procesamiento inteligente de imágenes mediante IA de visión computacional.

---

## 🚀 Arquitectura del Sistema

```
PartVision.app/
├── backend/          # API REST Java 21 / Spring Boot 3.4.1 (PostgreSQL + MinIO + Flyway)
├── web/              # Panel de Administración Web Angular 20 (Three.js + Tailwind CSS Neon-Dark)
└── mobile/           # Aplicación Móvil Flutter para Operarios (Escaneo + Captura IA)
```

---

## 🌟 Características Principales

### 🧠 Motor de Visión Computacional IA Pluggable
- **OpenAI Vision (`OpenAIVisionExtractor`)**: Extrae automáticamente código de pieza, marca, descripción, código de barras y detalles técnicos a partir de fotos de repuestos o cajas (utilizando el SDK oficial `com.openai:openai-java:4.49.0`).
- **Claude Vision (`ClaudeVisionExtractor`)** & **Stub Extractor**: Proveedores alternativos intercambiables mediante configuración.
- **Configuración (`.env`)**:
  ```env
  AI_VISION_PROVIDER=openai
  OPENAI_API_KEY=sk-...
  AI_VISION_MODEL=gpt-4o
  ```

### 💻 Panel Web de Administración (UI/UX Designer Neon-Dark)
- **Rediseño Futurista Neon-Dark**: Paleta de colores neón (violeta `#7c3aed`, cyan `#06b6d4`, rosa `#f0abfc`), glassmorphism y fuentes *Inter* + *JetBrains Mono*.
- **Three.js 3D Animations**:
  - `ThreeBgComponent`: Constelación de partículas 3D animadas y conexiones de energía en el login.
  - `ThreeLogoComponent`: Logotipo geométrico 3D de alambre rotativo en la barra lateral.
- **Gestión de Usuarios (Admin Control)**:
  - `GET /api/v1/usuarios`: Listado de todos los operadores y administradores.
  - `POST /api/v1/usuarios`: Alta de nuevos usuarios operarios.
  - `PATCH /api/v1/usuarios/{id}/activo`: Activar / desactivar estado de cuenta.
- **Catálogo, Stock & Ubicaciones**: ABM completo de productos, ubicaciones de depósito (jerarquía depósitos, pasillos, estantes), trazabilidad de movimientos e importación masiva CSV.

---

## 🛠️ Instrucciones de Ejecución

### 1. Backend (Spring Boot + PostgreSQL + MinIO)
```bash
# Levantar servicios de base de datos y almacenamiento con Docker
docker compose up -d

# Ejecutar backend
cd backend
mvn spring-boot:run
```

### 2. Panel Web (Angular 20)
```bash
cd web
npm install
npm start        # ng serve -> http://localhost:4200
```
- **Credenciales Dev**: `admin` / `admin1234`

---

## 🧪 Verificación y Tests

### Backend (189 Unit & Integration Tests)
```bash
cd backend
mvn test
```
*Todas las suites de prueba pasan al 100% verde (0 fallos, 0 errores).*

### Frontend (Compilación Angular + Tailwind)
```bash
cd web
npx ng build
```
*Build de producción verificado y libre de errores.*
