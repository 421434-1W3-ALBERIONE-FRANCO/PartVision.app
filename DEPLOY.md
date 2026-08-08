# Despliegue de PartVision

La app son **3 servicios**: una base **Postgres**, el **backend** (Spring Boot, `backend/Dockerfile`)
y el **panel web** (Angular + nginx, `web/Dockerfile`). Ambos Dockerfiles buildean solos desde el repo.

> Recomendado para que el cliente lo pruebe: **Render** (tiene tier gratis con subdominio + HTTPS).
> **Railway** funciona igual (más fluido, pero de pago). Los pasos son análogos.

## Orden de despliegue (importante)
1. Postgres → 2. Backend → 3. Web. (La web necesita la URL del backend, y el backend necesita la URL de la web para CORS.)

---

## Render — paso a paso

### 1) Base de datos Postgres
- New → **PostgreSQL**. Nombre `partvision-db`. Create.
- Copiá el **Internal Database URL** (formato `postgres://user:pass@host/db`).
  El backend usa formato JDBC, así que armá: `jdbc:postgresql://HOST:5432/DB` y guardá aparte user y pass.

### 2) Backend
- New → **Web Service** → conectá el repo de GitHub.
- **Root Directory**: `backend`  · **Runtime**: Docker (detecta `backend/Dockerfile`).
- **Environment variables** (Advanced):

  | Variable | Valor |
  |---|---|
  | `SPRING_PROFILES_ACTIVE` | `dev` (o `prod` si creás el perfil) |
  | `DB_URL` | `jdbc:postgresql://HOST:5432/DB` |
  | `DB_USERNAME` | *(user de la DB)* |
  | `DB_PASSWORD` | *(pass de la DB)* |
  | `JWT_SECRET` | *(cadena aleatoria de 32+ bytes)* |
  | `AI_VISION_PROVIDER` | `gemini` |
  | `GEMINI_API_KEY` | *(tu key)* |
  | `AI_VISION_MODEL` | `gemini-flash-latest` |
  | `CORS_ALLOWED_ORIGINS` | *(la URL de la web — se completa tras el paso 3)* |
  | `SPRINGDOC_SWAGGER_UI_ENABLED` | `false` |
  | `SPRINGDOC_API_DOCS_ENABLED` | `false` |

- Create. Anotá la URL que te da (ej. `https://partvision-api.onrender.com`).
- Healthcheck path: `/actuator/health` (es público).

### 3) Web
- New → **Web Service** → mismo repo.
- **Root Directory**: `web` · **Runtime**: Docker (`web/Dockerfile`).
- **Environment variables**:

  | Variable | Valor |
  |---|---|
  | `API_BASE_URL` | `https://partvision-api.onrender.com/api/v1` *(URL del backend + `/api/v1`)* |

- Create. Anotá la URL (ej. `https://partvision.onrender.com`).

### 4) Cerrar el círculo (CORS)
- Volvé al **backend** → editá `CORS_ALLOWED_ORIGINS` = la URL de la web (`https://partvision.onrender.com`, sin barra final). Redeploy del backend.

### 5) Listo
- Entrá a la URL de la web, login. Las migraciones Flyway crean el esquema solo en el primer arranque.

---

## Cargar el catálogo en producción
La base arranca vacía. Para cargar los 135k productos: logueado como **admin**, usá **Importación CSV**
(o el endpoint `POST /api/v1/importaciones/productos`, campo `archivo`) con los CSV de `data-import/`.
Es admin-only, así que lo hacés vos.

---

## ⚠️ Pendientes de seguridad ANTES de dárselo al cliente
1. **Cambiar la contraseña semilla `admin1234`** (está en la migración `V2__auth.sql`, es débil y figura en filtraciones).
   Como todavía no hay pantalla de cambio de contraseña, hacelo por SQL en la DB de prod:
   generá un hash BCrypt nuevo y `UPDATE usuarios SET password_hash='<hash>' WHERE username='admin';`
   *(Pendiente en el roadmap: flujo de cambio de contraseña + bootstrap del admin por variable de entorno.)*
2. **Swagger** ya queda apagado con las dos variables `SPRINGDOC_*=false` del paso 2.
3. **Secretos**: `JWT_SECRET`, `GEMINI_API_KEY`, credenciales de DB — solo como env vars en el panel, nunca en el repo.

## Nota sobre imágenes (IA)
Las fotos de la captura por IA hoy se guardan por `imagenKey` sin un endpoint que las sirva. Para producción
conviene un object storage (S3/R2/MinIO) — es un paso posterior, no bloquea el testeo del cliente.
