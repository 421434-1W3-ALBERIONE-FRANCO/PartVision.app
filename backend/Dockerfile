# --- Etapa 1: build (Maven + JDK 21) ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Cachea dependencias: primero el pom, luego el codigo.
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
# Tests requieren Postgres/Docker -> se corren en CI, no en la imagen.
RUN mvn -q -B clean package -DskipTests

# --- Etapa 2: runtime (JRE liviano) ---
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/partvision-backend.jar app.jar

# Puerto HTTP (la plataforma puede sobreescribir con $PORT).
EXPOSE 8080
ENV JAVA_OPTS=""

# Variables que el deploy DEBE definir (no hay defaults seguros en prod):
#   SPRING_PROFILES_ACTIVE=prod
#   DB_URL / DB_USERNAME / DB_PASSWORD   (Postgres gestionado)
#   JWT_SECRET                           (>=32 bytes, aleatorio)
#   CORS_ALLOWED_ORIGINS                 (URL del panel web)
#   AI_VISION_PROVIDER=gemini + GEMINI_API_KEY
# Respeta $PORT si la plataforma lo inyecta (Render/Railway).
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar app.jar"]
