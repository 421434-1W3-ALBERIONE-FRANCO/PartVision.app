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
# El heap es solo una parte: medido en el server (2026-10-13), la JVM ya usa ~320 MB fuera del heap
# (metaspace ~137 MB, codigo compilado, hilos, memoria nativa). Con 75% de 1 GB el heap podia crecer
# a 768 MB, heap + resto pasaba el limite del contenedor y el kernel mataba el proceso (SIGKILL, sin
# OutOfMemoryError ni log) en medio de la actualizacion de precios. 60% deja ~40% para lo de afuera:
# con los 1280m de redeploy.sh, el heap sigue en 768 MB y quedan 512 MB para el resto.
# MALLOC_ARENA_MAX=2: sin el, glibc abre un arena de memoria nativa por hilo y la fragmenta.
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=60.0 -XX:+UseSerialGC"
ENV MALLOC_ARENA_MAX=2

# Variables que el deploy DEBE definir (no hay defaults seguros en prod):
#   SPRING_PROFILES_ACTIVE=prod
#   DB_URL / DB_USERNAME / DB_PASSWORD   (Postgres gestionado)
#   JWT_SECRET                           (>=32 bytes, aleatorio)
#   CORS_ALLOWED_ORIGINS                 (URL del panel web)
#   AI_VISION_PROVIDER=gemini + GEMINI_API_KEY
# Respeta $PORT si la plataforma lo inyecta (Render/Railway).
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar app.jar"]
