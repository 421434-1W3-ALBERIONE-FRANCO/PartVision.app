# --- Etapa 1: build (Node) ---
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# --- Etapa 2: runtime (nginx sirve los estaticos) ---
FROM nginx:alpine
COPY --from=build /app/dist/partvision-web/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh
EXPOSE 80
# El entrypoint inyecta API_BASE_URL en runtime y respeta $PORT.
ENTRYPOINT ["/docker-entrypoint.sh"]
