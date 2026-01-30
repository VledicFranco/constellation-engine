# =============================================================================
# Constellation Engine - Multi-stage Docker Build
# =============================================================================
# Produces a minimal JRE-based image running the example HTTP server.
#
# Build:  docker build -t constellation-engine .
# Run:    docker run -p 8080:8080 constellation-engine
#
# Configuration is via environment variables (all optional):
#   CONSTELLATION_PORT                 (default: 8080)
#   CONSTELLATION_SCHEDULER_ENABLED    (default: false)
#   CONSTELLATION_SCHEDULER_MAX_CONCURRENCY (default: 16)
#   CONSTELLATION_API_KEYS             (comma-separated key:Role pairs)
#   CONSTELLATION_CORS_ORIGINS         (comma-separated origins)
#   CONSTELLATION_RATE_LIMIT_RPM       (default: 100)
#   CONSTELLATION_RATE_LIMIT_BURST     (default: 20)
# =============================================================================

# ---------------------------------------------------------------------------
# Stage 1: Build fat JAR with sbt-assembly
# ---------------------------------------------------------------------------
FROM eclipse-temurin:17-jdk AS builder

WORKDIR /build

# Copy build definition first for better layer caching
COPY project/ project/
COPY build.sbt .

# Fetch dependencies (cached unless build.sbt or plugins.sbt change)
RUN sbt update

# Copy source code and build
COPY modules/ modules/
COPY dashboard/ dashboard/
RUN sbt "exampleApp/assembly"

# ---------------------------------------------------------------------------
# Stage 2: Minimal runtime image
# ---------------------------------------------------------------------------
FROM eclipse-temurin:17-jre

WORKDIR /app

# Install curl for health checks
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Run as non-root user
RUN adduser --system --group constellation
COPY --from=builder /build/modules/example-app/target/scala-3.3.1/constellation-*.jar app.jar
USER constellation

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --retries=3 --start-period=30s \
  CMD curl -f http://localhost:8080/health/live || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
