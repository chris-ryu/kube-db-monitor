FROM eclipse-temurin:17-jre

# Create agent user
RUN groupadd -r agent && useradd -r -g agent agent

# Create agent directory
WORKDIR /opt/kubedb-agent

# Copy the agent JAR file
COPY target/kubedb-monitor-agent-*.jar kubedb-monitor-agent.jar

# Make sure the jar file exists and is readable
RUN ls -la /opt/kubedb-agent/kubedb-monitor-agent.jar && \
    chown agent:agent /opt/kubedb-agent/kubedb-monitor-agent.jar && \
    chmod 644 /opt/kubedb-agent/kubedb-monitor-agent.jar

# Switch to non-root user
USER agent

# Default command to help debug
CMD ["sh", "-c", "echo 'KubeDB Monitor Agent JAR available at /opt/kubedb-agent/kubedb-monitor-agent.jar' && ls -la /opt/kubedb-agent/kubedb-monitor-agent.jar"]