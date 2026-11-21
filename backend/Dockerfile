# ========================================================
# Build Stage: Maven Build with Temurin JDK 17
# ========================================================
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Cache dependency layer
COPY pom.xml ./
RUN mvn -B dependency:go-offline

# Compile and package application
COPY src src
RUN mvn -B package -DskipTests

# ========================================================
# Runtime Stage: Hardened, Least-Privilege JRE 17
# ========================================================
FROM eclipse-temurin:17-jre-jammy AS runtime
WORKDIR /app

# Install wget for container healthcheck and create non-root user
RUN apt-get update && \
    apt-get install -y --no-install-recommends wget && \
    rm -rf /var/lib/apt/lists/* && \
    groupadd -r -g 10001 hms && \
    useradd -r -u 10001 -g hms -s /sbin/nologin -d /app hms

# Copy compiled artifact with non-root ownership
COPY --from=build --chown=hms:hms /workspace/target/*.jar app.jar

# Enforce least-privilege non-root execution
USER hms:hms

EXPOSE 8080

# Health check probing internal readiness endpoint
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget -qO- http://localhost:8080/api/health/readiness || exit 1

# Container-aware JVM memory configuration
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["java", "-jar", "app.jar"]
