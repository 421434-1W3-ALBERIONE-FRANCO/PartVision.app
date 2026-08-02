# PartVision — Backend

API del sistema de gestión de inventario y digitalización.

- **Java 21** · **Spring Boot 3.4** · **PostgreSQL** · **Flyway** · **Maven**
- Arquitectura: monolito modular por dominio. `common/` (infraestructura transversal) y, en cada fase, módulos de negocio (`catalog/`, `inventory/`, …), cada uno con sus subcapas `domain / repository / service / dto / controller`.

## Estado

### Fase 1 — backend base
- Proyecto Spring Boot + configuración por perfiles (`dev`, `test`).
- `docker-compose.yml` (en la raíz del repo): PostgreSQL + MinIO + Adminer.
- Flyway habilitado (`V1__baseline.sql`).
- Auditoría JPA (`Auditable`, `@CreatedDate/@CreatedBy/…`).
- Manejo global de errores (`GlobalExceptionHandler` → `ApiError` uniforme).
- Bean Validation y documentación OpenAPI/Swagger.
- Suite de tests con **JaCoCo ≥95%** (LINE/BRANCH) exigido en `verify`.

### Fase 2 — autenticación y usuarios
- Usuarios, roles (N:M) y login con **JWT stateless** (HS256), BCrypt para passwords.
- `SecurityConfig`: sin sesión de servidor; públicos `/api/v1/auth/**`, `/actuator/health`, Swagger; el resto requiere token. 401 uniforme vía `RestAuthenticationEntryPoint`.
- `AuditorAwareImpl` conectado al usuario autenticado (alimenta `created_by`/`updated_by`).
- Migración `V2__auth.sql` con seed de roles y un **usuario admin de bootstrap**.

**Endpoints:**
- `POST /api/v1/auth/register` → 201 (crea usuario con rol `OPERARIO`).
- `POST /api/v1/auth/login` → 200 `{ token, expiresIn }`.
- `GET /api/v1/usuarios/me` → 200 (requiere `Authorization: Bearer <token>`).

**Usuario de bootstrap (solo desarrollo):** `admin` / `admin1234`. Cambiar en producción y definir `JWT_SECRET`.

Prueba rápida del login (con la infra levantada y el backend corriendo):

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" -d "{\"username\":\"admin\",\"password\":\"admin1234\"}"
```

### Fase 3 — catálogo
- **Marcas**, **categorías** (jerárquicas, con `parentId` opcional) y **productos**.
- Producto: `sku` (opcional), `marcaId`/`categoriaId` (opcionales), `descripcion`, `estado` (`BORRADOR`/`ACTIVO`/`INACTIVO`), `detallesExtra` (JSONB flexible) y **códigos** (un producto → N códigos: barras/interno/proveedor).
- Reglas: SKU único por marca (D2), código único global (D1), anti-duplicados con 409.

**Endpoints (todos requieren `Authorization: Bearer <token>`):**
- `POST/GET /api/v1/marcas`, `GET /api/v1/marcas/{id}`
- `POST/GET /api/v1/categorias`, `GET /api/v1/categorias/{id}`
- `POST /api/v1/productos`, `GET /api/v1/productos/{id}`, `GET /api/v1/productos` (paginado)
- `GET /api/v1/productos/buscar?codigo=<barcode>` → producto o 404 (flujo de escaneo)

### Fase 4 — ubicaciones
- Árbol jerárquico flexible (Depósito → Sector → Pasillo → Estantería → Nivel) con `path` materializado (ej `A/1/4/2`).
- Un tipo no puede colgar de otro de igual o mayor profundidad (422). Código único entre hermanos.

**Endpoints (requieren token):**
- `POST /api/v1/ubicaciones` (body: `tipo`, `codigo`, `parentId?`)
- `GET /api/v1/ubicaciones` (raíces), `GET /api/v1/ubicaciones/{id}`, `GET /api/v1/ubicaciones/{id}/hijos`

### Fase 5 — stock y movimientos
- Stock por **(producto, ubicación)**; el historial vive en `movimientos_stock` (append-only).
- Operaciones con **lock pesimista** (sin condiciones de carrera), **sin stock negativo** (422) y con movimiento auditable por cada cambio.
- Transferencia: adquiere locks en orden ascendente de ubicación (evita deadlocks).

**Endpoints (requieren token):**
- `POST /api/v1/stock/entradas`, `/salidas`, `/ajustes` (tipo `AJUSTE_POSITIVO`/`AJUSTE_NEGATIVO`, motivo obligatorio), `/transferencias` → 201 con el movimiento.
- `GET /api/v1/stock?productoId=<id>` → total + desglose por ubicación (flujo de escaneo).
- `GET /api/v1/movimientos?productoId=<id>` → historial paginado.

## Requisitos

- JDK 21, Maven 3.9+, Docker (para la base y los tests de integración con Testcontainers).

## Cómo ejecutar

1. Levantar la infraestructura de desarrollo (desde la raíz del repo):

```bash
docker compose up -d
```

2. Arrancar el backend (perfil `dev` por defecto):

```bash
cd backend && mvn spring-boot:run
```

3. Verificar que responde:

- Health: http://localhost:8080/actuator/health → `{"status":"UP"}`
- Swagger UI: http://localhost:8080/swagger-ui.html
- Adminer (inspección de la DB): http://localhost:8081 (sistema PostgreSQL, servidor `db`, usuario/clave `partvision`)

## Cómo testear

```bash
cd backend && mvn clean verify
```

- Ejecuta unitarios + `@WebMvcTest` + un smoke test de contexto sobre un PostgreSQL real vía **Testcontainers** (requiere Docker; si no está disponible, ese test se **salta** sin romper el build).
- Falla el build si la cobertura baja de 95% (LINE/BRANCH).
- Reporte HTML: `backend/target/site/jacoco/index.html`.

## Variables de entorno

Ver [`.env.example`](.env.example). Los valores por defecto apuntan al `docker-compose` local. Nada de secretos hardcodeados en el código.
