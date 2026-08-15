// Config de runtime. Por defecto RELATIVA: el navegador pega contra su propio origen
// y el proxy reenvia al backend (mismo dominio => cookie HttpOnly + CSRF sin exponer CORS).
// El contenedor puede reescribir este archivo con API_BASE_URL solo si se necesita apuntar
// a un backend en otro dominio (no recomendado: pierde el blindaje same-origin).
window.__env = { apiBaseUrl: '/api/v1' };
