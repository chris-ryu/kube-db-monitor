# KubeDB Monitor - Demo Scenario

## Overview
This document provides a comprehensive demo scenario for validating the KubeDB Monitor solution. The demo showcases automatic database monitoring injection into a sample university course registration application.

## Prerequisites
- Kubernetes cluster (v1.19+)
- KubeDB Monitor Controller deployed
- Sample University Registration application
- Access to cluster via kubectl
- Ingress controller configured

## Demo Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    University Registration App                  │
│                                                                │
│  ┌──────────────┐    ┌─────────────────┐    ┌──────────────┐ │
│  │   Frontend   │────│   Spring Boot   │────│   H2 Database│ │
│  │    (Web)     │    │   Application   │    │   (In-Memory)│ │
│  └──────────────┘    └─────────────────┘    └──────────────┘ │
│                              │                                │
│                              ▼                                │
│                    ┌─────────────────┐                       │
│                    │  KubeDB Agent   │                       │
│                    │  (Injected)     │                       │
│                    └─────────────────┘                       │
└─────────────────────────────────────────────────────────────────┘
                               │
                               ▼
          ┌─────────────────────────────────────┐
          │        KubeDB Monitor               │
          │    - JDBC Interception             │
          │    - Performance Metrics           │
          │    - Database Query Monitoring     │
          └─────────────────────────────────────┘
```

## Demo Scenario Steps

### Step 1: Deploy the Base Application

1. **Deploy university registration application without monitoring:**
   ```bash
   # Deploy basic application
   kubectl apply -f k8s/university-simple.yaml
   
   # Check deployment status
   kubectl get pods -n kubedb-monitor-test
   kubectl logs -f deployment/university-registration-simple -n kubedb-monitor-test
   ```

2. **Verify application is working:**
   ```bash
   # Test health endpoint
   curl -k https://university-registration.bitgaram.info/api/actuator/health
   
   # Access the application UI
   open https://university-registration.bitgaram.info
   ```

### Step 2: Enable KubeDB Monitoring

1. **Add monitoring annotations to the deployment:**
   ```yaml
   metadata:
     annotations:
       kubedb.monitor/enable: "true"
       kubedb.monitor/db-types: "h2"
       kubedb.monitor/sampling-rate: "1.0"
       kubedb.monitor/slow-query-threshold: "100"
       kubedb.monitor/collector-type: "logging"
   ```

2. **Apply the monitored configuration:**
   ```bash
   kubectl apply -f k8s/university-monitored.yaml
   ```

3. **Verify agent injection:**
   ```bash
   # Check for init container and agent injection
   kubectl describe pod -n kubedb-monitor-test -l app=university-registration
   
   # Verify agent logs
   kubectl logs -n kubedb-monitor-test -l app=university-registration -c kubedb-agent-init
   ```

### Step 3: Generate Database Activity

1. **Student Registration Scenario:**
   ```bash
   # Register multiple students
   for i in {1..5}; do
     curl -X POST https://university-registration.bitgaram.info/api/students \
       -H "Content-Type: application/json" \
       -d '{
         "name": "Student '$i'",
         "email": "student'$i'@university.edu",
         "studentId": "STU00'$i'"
       }'
   done
   ```

2. **Course Creation Scenario:**
   ```bash
   # Create sample courses
   curl -X POST https://university-registration.bitgaram.info/api/courses \
     -H "Content-Type: application/json" \
     -d '{
       "courseCode": "CS101",
       "courseName": "Introduction to Computer Science",
       "credits": 3,
       "maxEnrollment": 30,
       "semester": "Fall 2025"
     }'
   
   curl -X POST https://university-registration.bitgaram.info/api/courses \
     -H "Content-Type: application/json" \
     -d '{
       "courseCode": "MATH201",
       "courseName": "Calculus II",
       "credits": 4,
       "maxEnrollment": 25,
       "semester": "Fall 2025"
     }'
   ```

3. **Course Enrollment Scenario:**
   ```bash
   # Enroll students in courses
   curl -X POST https://university-registration.bitgaram.info/api/enrollments \
     -H "Content-Type: application/json" \
     -d '{
       "studentId": 1,
       "courseId": 1
     }'
   
   curl -X POST https://university-registration.bitgaram.info/api/enrollments \
     -H "Content-Type: application/json" \
     -d '{
       "studentId": 1,
       "courseId": 2
     }'
   ```

4. **Generate Slow Queries:**
   ```bash
   # Access reports that might trigger complex queries
   curl https://university-registration.bitgaram.info/api/reports/enrollment-summary
   curl https://university-registration.bitgaram.info/api/reports/student-details
   ```

### Step 4: Monitor Database Activity

1. **Check KubeDB Agent Logs:**
   ```bash
   # View database monitoring logs
   kubectl logs -n kubedb-monitor-test -l app=university-registration --tail=50 | grep "KUBEDB-MONITOR"
   ```

   Expected output:
   ```
   [KUBEDB-MONITOR] Query executed: SELECT * FROM students WHERE id = ? | Time: 45ms | DB: h2 | Success: true
   [KUBEDB-MONITOR] Query executed: INSERT INTO enrollments (student_id, course_id, enrollment_date) VALUES (?, ?, ?) | Time: 23ms | DB: h2 | Success: true
   [KUBEDB-MONITOR] SLOW QUERY: SELECT s.*, c.* FROM students s JOIN enrollments e ON s.id = e.student_id JOIN courses c ON e.course_id = c.id | Time: 156ms | DB: h2 | Success: true
   ```

2. **Monitor Performance Metrics:**
   ```bash
   # Check JMX metrics if configured
   kubectl port-forward -n kubedb-monitor-test svc/university-registration-service 9090:9090
   curl http://localhost:9090/actuator/metrics/kubedb.monitor.queries.total
   ```

### Step 5: Validation Scenarios

#### Scenario A: High Load Testing
```bash
# Generate high database load
for i in {1..100}; do
  curl https://university-registration.bitgaram.info/api/students &
done
wait

# Check monitoring captured the load
kubectl logs -n kubedb-monitor-test -l app=university-registration --tail=200 | grep "KUBEDB-MONITOR" | wc -l
```

#### Scenario B: Slow Query Detection
```bash
# Trigger complex query that should be flagged as slow
curl https://university-registration.bitgaram.info/api/reports/detailed-analytics

# Verify slow query detection
kubectl logs -n kubedb-monitor-test -l app=university-registration | grep "SLOW QUERY"
```

#### Scenario C: Error Handling
```bash
# Attempt invalid operations to trigger database errors
curl -X POST https://university-registration.bitgaram.info/api/students \
  -H "Content-Type: application/json" \
  -d '{}'

# Check error monitoring
kubectl logs -n kubedb-monitor-test -l app=university-registration | grep "Error\|Exception"
```

## Expected Results

### 1. Agent Injection Verification
- ✅ Init container `kubedb-agent-init` should be automatically added
- ✅ Agent JAR should be copied to shared volume
- ✅ Java application should start with agent attached
- ✅ Agent should log "KubeDB Monitor Agent started successfully"

### 2. Database Monitoring
- ✅ All database queries should be intercepted and logged
- ✅ Query execution times should be recorded
- ✅ Slow queries (>100ms) should be flagged
- ✅ Database operations should include connection info
- ✅ SQL parameters should be masked for security

### 3. Performance Impact
- ✅ Application startup time should increase by <5 seconds
- ✅ Query performance overhead should be <5%
- ✅ Memory usage should increase by <50MB
- ✅ No functional impact on application behavior

### 4. Metrics Collection
- ✅ Total query count should be available
- ✅ Average query execution time should be calculated
- ✅ Slow query count should be tracked
- ✅ Database error count should be monitored

## Demo Checklist

### Pre-Demo Setup
- [ ] Verify Kubernetes cluster is accessible
- [ ] Confirm KubeDB Monitor Controller is running
- [ ] Check ingress controller and SSL certificates
- [ ] Prepare demo data and scripts
- [ ] Test all curl commands in advance

### During Demo
- [ ] Show baseline application without monitoring
- [ ] Demonstrate agent injection via annotations
- [ ] Generate realistic database activity
- [ ] Display monitoring logs and metrics
- [ ] Highlight slow query detection
- [ ] Show error handling capabilities

### Post-Demo Validation
- [ ] Confirm all monitoring data was captured
- [ ] Verify no data loss or corruption
- [ ] Check application performance metrics
- [ ] Document any issues or observations

## Troubleshooting

### Common Issues

1. **Agent Not Injected:**
   - Check webhook configuration: `kubectl get mutatingwebhookconfigurations`
   - Verify namespace labels: `kubectl describe ns kubedb-monitor-test`
   - Confirm controller is running: `kubectl get pods -n kubedb-monitor`

2. **Application Won't Start:**
   - Check Java version compatibility (requires 17+)
   - Verify agent JAR permissions and path
   - Review memory and CPU limits

3. **No Monitoring Data:**
   - Confirm database operations are happening
   - Check agent configuration parameters
   - Verify logging level settings

4. **Performance Issues:**
   - Reduce sampling rate if overhead is too high
   - Adjust slow query threshold
   - Monitor resource usage

### Debugging Commands

```bash
# Check controller logs
kubectl logs -n kubedb-monitor deployment/kubedb-monitor-controller

# Verify webhook admission
kubectl get events -n kubedb-monitor-test --sort-by='.lastTimestamp'

# Test webhook directly
kubectl auth can-i create pods --as=system:serviceaccount:kubedb-monitor-test:default

# Agent troubleshooting
kubectl exec -n kubedb-monitor-test deployment/university-registration -- ls -la /opt/kubedb-agent/
```

## Success Criteria

This demo is considered successful if:

1. **Automatic Injection:** Agent is automatically injected into pods with monitoring annotations
2. **Database Monitoring:** All database operations are captured and logged
3. **Performance Tracking:** Query execution times and slow queries are identified
4. **No Functional Impact:** Application continues to work normally with monitoring enabled
5. **Comprehensive Coverage:** All supported database operations are monitored
6. **Error Handling:** Database errors are properly captured and reported

## Next Steps

After successful demo validation:
1. Deploy to production environment with appropriate configuration
2. Set up monitoring dashboards and alerting
3. Implement log aggregation and analysis
4. Configure performance baselines and SLAs
5. Train operations team on monitoring capabilities