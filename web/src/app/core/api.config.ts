// URL base del backend, centralizada (no hardcodear en cada service).
// Por defecto es RELATIVA ('/api/v1'): el navegador pega contra su propio origen y
// el proxy (nginx en prod, proxy.conf.json en dev) reenvia al backend. Eso mantiene
// todo same-origin, que es lo que permite la cookie HttpOnly + CSRF sin exponer CORS.
// Se puede sobreescribir por runtime (public/env.js -> API_BASE_URL) si hiciera falta
// apuntar a un backend en otro dominio.
declare global {
  interface Window {
    __env?: { apiBaseUrl?: string };
  }
}

export const API_BASE_URL = window.__env?.apiBaseUrl ?? '/api/v1';
