# One image: React bundle served by Spring Boot, so app and API share an origin and the
# refresh cookie can stay SameSite=Strict with no CORS configuration.

# --- 1. Frontend ---------------------------------------------------------------------
FROM node:20-alpine AS web
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# --- 2. Backend ----------------------------------------------------------------------
FROM eclipse-temurin:21-jdk AS api
WORKDIR /api
COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY backend/src src
COPY --from=web /web/dist src/main/resources/static
# Tests need a real Postgres (Testcontainers); they run in CI and locally, not in the image build.
RUN ./mvnw -B -q package -DskipTests

# --- 3. Runtime ----------------------------------------------------------------------
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 1001 plantdesk
WORKDIR /app
COPY --from=api /api/target/plantdesk-0.1.0.jar app.jar
USER plantdesk
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
