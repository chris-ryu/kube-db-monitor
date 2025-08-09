FROM eclipse-temurin:17-jre

LABEL maintainer="KubeDB Monitor Team"
LABEL description="University Course Registration System - Sample Application for KubeDB Monitor Testing"

# Create application user
RUN groupadd -r appuser && useradd -r -g appuser appuser

# Set working directory
WORKDIR /app

# Copy the application JAR
COPY target/course-registration-*.jar app.jar

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