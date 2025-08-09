FROM eclipse-temurin:17-jre

LABEL maintainer="KubeDB Monitor Team"
LABEL description="KubeDB Monitor Admission Controller - Kubernetes Webhook for automatic Java Agent injection"

# Create application user
RUN groupadd -r controller && useradd -r -g controller controller

# Set working directory
WORKDIR /app

# Copy the application JAR
COPY target/kubedb-monitor-controller-*.jar app.jar

# Create logs and certs directories
RUN mkdir -p /app/logs /app/certs && chown -R controller:controller /app

# Switch to application user
USER controller

# Expose the webhook and metrics ports
EXPOSE 8443 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/healthz || exit 1

# Environment variables
ENV JAVA_OPTS="-Xmx256m -Xms128m" \
    SERVER_PORT=8443 \
    METRICS_PORT=8080 \
    LOG_LEVEL=INFO

# Start the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]