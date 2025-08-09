# KubeDB Monitor

A comprehensive Kubernetes DB performance monitoring system similar to Jennifer APM, built with Test-Driven Development (TDD) approach.

## Overview

KubeDB Monitor automatically injects Java agents into Kubernetes pods to monitor database performance with minimal configuration. It uses annotation-based detection to identify pods that should be monitored and provides comprehensive metrics collection for database operations.

## Features

- **Annotation-Based Configuration**: Simple annotations to enable monitoring
- **Automatic Agent Injection**: Kubernetes admission controller automatically injects monitoring agents
- **JDBC Interception**: ASM-based bytecode manipulation for monitoring database calls
- **Multiple Database Support**: MySQL, PostgreSQL, H2, Oracle, and more
- **Flexible Metrics Collection**: In-memory, logging, and JMX output formats
- **Kubernetes Native**: Designed specifically for Kubernetes environments
- **OpenJDK 17+ Compatible**: Built for modern Java environments

## Architecture

### Components

1. **kubedb-monitor-common**: Shared utilities and metrics definitions
2. **kubedb-monitor-agent**: Java agent for JDBC interception using ASM
3. **kubedb-monitor-controller**: Kubernetes admission webhook controller

### How It Works

1. **Annotation Detection**: Pods with `kubedb.monitor/enable: "true"` are detected
2. **Agent Injection**: Admission controller injects the monitoring agent into containers
3. **JDBC Monitoring**: Agent intercepts JDBC calls using bytecode manipulation
4. **Metrics Collection**: Database metrics are collected and output via configured collectors

## Usage

### 1. Pod Annotations

Add these annotations to your pod templates:

```yaml
metadata:
  annotations:
    kubedb.monitor/enable: "true"                    # Enable monitoring
    kubedb.monitor/db-types: "mysql,postgresql"     # Target database types
    kubedb.monitor/collector-type: "logging"        # Metrics output format
    kubedb.monitor/slow-query-threshold: "1000"     # Slow query threshold (ms)
    kubedb.monitor/sampling-rate: "0.8"             # Sampling rate (0.0-1.0)
```

### 2. Deployment Example

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: my-java-app
spec:
  template:
    metadata:
      annotations:
        kubedb.monitor/enable: "true"
        kubedb.monitor/db-types: "mysql"
        kubedb.monitor/collector-type: "logging"
    spec:
      containers:
      - name: app
        image: my-java-app:latest
        # Agent will be automatically injected
```

## Configuration Options

### Pod Annotations

| Annotation | Description | Default | Example |
|------------|-------------|---------|---------|
| `kubedb.monitor/enable` | Enable monitoring | `false` | `"true"` |
| `kubedb.monitor/db-types` | Target database types | `"all"` | `"mysql,postgresql"` |
| `kubedb.monitor/collector-type` | Metrics output format | `"logging"` | `"jmx"`, `"memory"`, `"composite"` |
| `kubedb.monitor/slow-query-threshold` | Slow query threshold (ms) | `1000` | `"2000"` |
| `kubedb.monitor/sampling-rate` | Sampling rate (0.0-1.0) | `1.0` | `"0.5"` |

### Supported Database Types

- `mysql` - MySQL/MariaDB
- `postgresql` - PostgreSQL
- `h2` - H2 Database
- `oracle` - Oracle Database
- `sqlserver` - Microsoft SQL Server
- `sqlite` - SQLite

### Metrics Collector Types

- `logging` - Output to application logs
- `jmx` - Expose via JMX MBeans
- `memory` - Store in memory (for testing)
- `composite` - Combine multiple collectors

## Installation

### Prerequisites

- Kubernetes 1.19+
- OpenJDK 17+
- Maven 3.6+
- Docker

### Build and Deploy

1. **Build the project:**
   ```bash
   mvn clean install
   ```

2. **Build Docker images:**
   ```bash
   ./scripts/build-images.sh
   ```

3. **Deploy to Kubernetes:**
   ```bash
   # Create namespace
   kubectl apply -f k8s/namespace.yaml
   
   # Create registry secret for private registry
   kubectl apply -f k8s/registry-secret.yaml
   
   # Create RBAC
   kubectl apply -f k8s/rbac.yaml
   
   # Deploy controller
   kubectl apply -f k8s/deployment.yaml
   
   # Configure webhooks
   kubectl apply -f k8s/webhook-config.yaml
   ```

4. **Test with example app:**
   ```bash
   kubectl apply -f k8s/example-app.yaml
   ```

## Development

### Project Structure

```
kube-db-monitor/
├── kubedb-monitor-common/          # Shared utilities and metrics
├── kubedb-monitor-agent/           # Java agent with ASM bytecode manipulation
├── kubedb-monitor-controller/      # Kubernetes admission webhook
├── k8s/                            # Kubernetes manifests
├── scripts/                        # Build and deployment scripts
└── src/test/                       # Integration tests
```

### Running Tests

```bash
# Run all tests
mvn test

# Run specific module tests
cd kubedb-monitor-agent && mvn test

# Run integration tests
mvn test -Dtest=IntegrationTest
```

### TDD Development

This project was built using Test-Driven Development (TDD):

1. **Red**: Write failing tests first
2. **Green**: Implement minimal code to pass tests
3. **Refactor**: Improve code while keeping tests green

Total test coverage: 35+ unit tests across all modules.

## Monitoring and Observability

### JMX Metrics

When using `jmx` collector type, metrics are exposed via JMX MBeans:

```
ObjectName: io.kubedb.monitor:type=DBMetrics
Attributes:
- TotalQueries: Total number of database queries
- SlowQueries: Number of slow queries
- AverageExecutionTime: Average query execution time
- ErrorCount: Number of database errors
```

### Log Output

When using `logging` collector type, metrics are written to application logs:

```
[KUBEDB-MONITOR] Query executed: SELECT * FROM users WHERE id = ? | 
Time: 45ms | DB: mysql | Success: true
```

### Health Checks

The controller exposes health endpoints:

```
GET /actuator/health          # Overall health
GET /actuator/health/readiness # Readiness probe
GET /actuator/metrics         # Prometheus metrics
```

## Troubleshooting

### Common Issues

1. **Agent not injected**: Verify pod has correct annotations and webhook is configured
2. **JMX conflicts**: JMX collector may conflict in test environments; use unique ObjectNames
3. **Bytecode modification fails**: Ensure Java 17+ and proper module configuration

### Debug Mode

Enable debug logging in the controller:

```yaml
env:
- name: LOGGING_LEVEL_IO_KUBEDB_MONITOR
  value: DEBUG
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Write tests first (TDD approach)
4. Implement functionality
5. Ensure all tests pass
6. Submit a pull request

## License

This project is licensed under the MIT License. See LICENSE file for details.

## Architecture Diagram

```
┌─────────────────┐    ┌────────────────────┐    ┌──────────────┐
│   Kubernetes    │    │   Admission        │    │   Java       │
│   Pod Creation  │───▶│   Controller       │───▶│   Agent      │
└─────────────────┘    └────────────────────┘    └──────────────┘
                                │                         │
                                ▼                         ▼
                       ┌────────────────────┐    ┌──────────────┐
                       │   Annotation       │    │   JDBC       │
                       │   Detection        │    │   Interception│
                       └────────────────────┘    └──────────────┘
                                                         │
                                                         ▼
                                                ┌──────────────┐
                                                │   Metrics    │
                                                │   Collection │
                                                └──────────────┘
```

## Performance Impact

- **Memory overhead**: ~10-20MB per monitored JVM
- **CPU overhead**: <1% under normal load
- **Network overhead**: Minimal (only webhook calls)
- **Startup delay**: <2 seconds additional startup time