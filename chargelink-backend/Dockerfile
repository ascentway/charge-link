# ──────────────────────────────────────────────────────────────────────────────
# Stage 1 – Build
# Uses the Maven wrapper to compile and package the JAR.
# ──────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper and pom first (layer-cache friendly)
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Fix Windows CRLF → LF (sed is always available on Alpine, no extra package)
# Then make the wrapper executable
RUN sed -i 's/\r$//' ./mvnw && chmod +x ./mvnw

# Download dependencies without building (cached Docker layer)
RUN ./mvnw dependency:go-offline -q

# Copy source
COPY src ./src

# Run unit tests first — if any test fails, the build stops here.
# Integration tests (@Tag("integration")) are excluded because they
# need Testcontainers / Docker-in-Docker which isn't available during image build.
RUN ./mvnw test -Dgroups='!integration' -q

# Tests passed → package the fat JAR (skip tests again, already ran above)
RUN ./mvnw package -DskipTests -q

# Spring Boot produces TWO jars:
#   chargelink-backend-0.1.0-SNAPSHOT.jar          (the fat executable JAR)
#   chargelink-backend-0.1.0-SNAPSHOT-plain.jar    (classes only, NOT runnable)
# We need the fat JAR. Rename it to a fixed name for the runtime stage.
RUN cp target/chargelink-backend-*.jar target/app.jar 2>/dev/null || \
    cp $(ls target/*.jar | grep -v plain | head -1) target/app.jar

# ──────────────────────────────────────────────────────────────────────────────
# Stage 2 – Runtime
# Minimal JRE image — only the application JAR, no build tools.
# ──────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Non-root user for security best practice
RUN addgroup -S chargelink && adduser -S chargelink -G chargelink
USER chargelink

WORKDIR /app

# Copy only the fat JAR from the build stage
COPY --from=builder /app/target/app.jar app.jar

# Expose the Spring Boot default port
EXPOSE 8080

# JVM tuning for containers:
#   UseContainerSupport  → reads Docker memory/CPU limits (not host values)
#   MaxRAMPercentage=75  → heap can use up to 75% of the container's RAM limit
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
