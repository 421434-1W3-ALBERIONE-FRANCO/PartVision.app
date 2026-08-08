// Config de runtime. En desarrollo apunta al backend local.
// En producción, el contenedor reescribe este archivo con la variable API_BASE_URL.
window.__env = { apiBaseUrl: 'http://localhost:8080/api/v1' };
