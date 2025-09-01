# =====================================================
# Stage 1: Maven Build Stage (빌드용 이미지)
# =====================================================
FROM eclipse-temurin:17-jdk AS builder

LABEL maintainer="KubeDB Monitor Team"
LABEL stage="build"

# Install Maven
RUN apt-get update && \
    apt-get install -y maven && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /build

# Copy Maven configuration files first (for better layer caching)
COPY pom.xml ./
COPY src/main/resources/application.yml ./src/main/resources/

# Download dependencies (이 레이어는 pom.xml이 변경되지 않으면 캐시됨)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN mvn clean package -DskipTests -B && \
    ls -la target/ && \
    find target/ -name "*.jar" -type f

# =====================================================  
# Stage 2: Runtime Stage (실행용 경량 이미지)
# =====================================================
FROM eclipse-temurin:17-jre AS runtime

LABEL maintainer="KubeDB Monitor Team"
LABEL description="University Course Registration System - Sample Application for KubeDB Monitor Testing"
LABEL stage="runtime"

# Create application user
RUN groupadd -r appuser && useradd -r -g appuser appuser

# Set working directory
WORKDIR /app

# Copy the application JAR from builder stage
COPY --from=builder /build/target/course-registration-*.jar app.jar

# Create logs directory
RUN mkdir -p /app/logs && chown -R appuser:appuser /app

# Switch to application user
USER appuser

# Expose the application port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/api/data/health || exit 1

# Environment variables for KubeDB Monitor
ENV JAVA_OPTS="-Xmx512m -Xms256m" \
    SPRING_PROFILES_ACTIVE=kubernetes \
    LOGGING_LEVEL_ROOT=INFO \
    LOGGING_LEVEL_COM_UNIVERSITY_REGISTRATION=DEBUG

# Start the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]