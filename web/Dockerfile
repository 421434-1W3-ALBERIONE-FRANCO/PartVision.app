# --- Etapa 1: build (Node) ---
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
# La app no se sirve en la raiz del dominio: nginx la publica bajo /partvision/
# porque el host comparte dominio con otro proyecto. Sin el base-href, Angular
# apunta los assets y el router a / y la web queda rota.
ARG BASE_HREF=/partvision/
RUN npx ng build --base-href ${BASE_HREF}

# --- Etapa 2: runtime (nginx sirve los estaticos) ---
FROM nginx:alpine
COPY --from=build /app/dist/partvision-web/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh
EXPOSE 80
# El entrypoint inyecta API_BASE_URL en runtime y respeta $PORT.
ENTRYPOINT ["/docker-entrypoint.sh"]
