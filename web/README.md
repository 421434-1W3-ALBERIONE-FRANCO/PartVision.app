# PartVision — Panel Web (Angular 20 + Three.js + Tailwind CSS)

Panel de administración enterprise con diseño futurista Neon-Dark: productos, stock/movimientos, ubicaciones, revisión de extracciones IA, importación masiva CSV y gestión completa de usuarios. Consume el backend real (Spring Boot) autenticado via JWT.

## Requisitos
- Node.js y npm (probado con Node 20 / 24).
- Backend corriendo (por defecto en `http://localhost:8080`).

## Ejecutar
```bash
cd web
npm install
npm start        # ng serve -> http://localhost:4200
```
- Credenciales Dev: `admin` / `admin1234`.

## UI/UX & Componentes 3D
- **Tailwind CSS**: Estilos globales dark (`#0a0a0f`), tarjetas glassmorphism, botones y badges neón.
- **Three.js Background (`three-bg.component.ts`)**: Fondo 3D animado de red de partículas en la pantalla de inicio de sesión.
- **Three.js Logo (`three-logo.component.ts`)**: Logotipo 3D geométrico rotativo en el sidebar principal.

## Estructura
```
src/app/
├── core/       api.config, models, services (usuario, producto, stock, ubicacion, extraccion, import),
│               auth (service + interceptor JWT + guard), three-bg, three-logo
└── features/   login, layout, productos, stock, ubicaciones, extracciones,
                importacion, usuarios (standalone components)
```

## Estado
- `npx ng build` compila limpiamente (verificado).
- Gestión completa de usuarios (listado, alta y toggle activo/inactivo).
