// URL base del backend, centralizada (no hardcodear en cada service).
// Se toma de la config de runtime (public/env.js), que el contenedor reescribe
// en produccion segun la variable API_BASE_URL. Fallback: backend local.
declare global {
  interface Window {
    __env?: { apiBaseUrl?: string };
  }
}

export const API_BASE_URL = window.__env?.apiBaseUrl ?? 'http://localhost:8080/api/v1';
