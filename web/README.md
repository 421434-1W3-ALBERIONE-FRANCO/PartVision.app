# PartVision — Panel web (Angular 20)

Panel de administración: productos, stock/movimientos, ubicaciones, revisión de
extracciones IA, importación masiva y alta de usuarios. Consume el backend real
(Spring Boot) con JWT. Sin datos mockeados.

## Requisitos
- Node.js y npm (probado con Node 24).
- Backend corriendo (por defecto en `http://localhost:8080`).

## Ejecutar
```bash
cd web
npm install
npm start        # ng serve -> http://localhost:4200
```
Login de dev: `admin` / `admin1234`.

## Configuración
- URL base del backend: `src/app/core/api.config.ts` (`API_BASE_URL`, default `http://localhost:8080/api/v1`).
- El backend permite CORS desde `http://localhost:4200` (configurable con `CORS_ALLOWED_ORIGINS`).

## Estructura
```
src/app/
├── core/       api.config, models (espejo de DTOs), services (1 por dominio),
│               auth (service + interceptor JWT + guard)
└── features/   login, layout, productos, stock, ubicaciones, extracciones,
                importacion, usuarios (standalone components)
```

## Estado
- `ng build` compila sin errores (verificado).
- No probado aún en navegador contra el backend en vivo; al levantarlo con `npm start`
  se validan login + flujos. Endpoints ya validados desde el backend.
- `gestión de usuarios`: por ahora solo alta (el backend aún no expone listado de usuarios).
