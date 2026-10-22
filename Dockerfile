# ── Stage 1: Build & Package ──────────────────────────────────────────────
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Cache dependencies layer
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Compile and package application
COPY src ./src
RUN mvn clean package -DskipTests -B

# ── Stage 2: Production Runtime ──────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Security: Create non-root unprivileged service account
RUN addgroup -S appgroup -g 10001 && \
    adduser -S appuser -u 10001 -G appgroup

WORKDIR /app

# Copy executable jar from builder stage
COPY --from=builder --chown=appuser:appgroup /build/target/*.jar app.jar

# Drop privileges to non-root user
USER appuser:appgroup

# Container ergonomics: JVM container awareness and memory limits
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -Djava.security.egd=file:/dev/./urandom"
ENV PORT=8080

EXPOSE 8080

# Production Healthcheck using Actuator endpoint
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
