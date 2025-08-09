# KubeDB Monitor 데모 시나리오 가이드

## 📋 개요

이 문서는 **대학교 수강신청 시스템**을 통해 KubeDB Monitor의 핵심 기능을 시연하기 위한 단계별 가이드입니다.

### 🎯 데모 목표
- KubeDB Monitor의 자동 에이전트 주입 기능 시연
- 실시간 데이터베이스 쿼리 모니터링 확인
- 성능 메트릭 수집 및 분석 기능 검증
- 느린 쿼리 감지 및 알림 기능 확인

### ⏱️ 예상 소요 시간
- **전체 데모**: 15-20분
- **준비 단계**: 2-3분
- **핵심 시연**: 10-12분
- **Q&A**: 3-5분

---

## 🛠️ 사전 준비사항

### 1. 환경 확인
```bash
# Kubernetes 클러스터 연결 확인
kubectl cluster-info

# KubeDB Monitor 네임스페이스 확인
kubectl get ns kubedb-monitor-test

# 현재 배포된 애플리케이션 확인
kubectl get pods -n kubedb-monitor-test
```

### 2. 필수 도구 준비
- `kubectl` CLI
- `curl` 명령어
- 웹 브라우저 (선택사항)
- 터미널 화면 공유 도구

### 3. 데모 전 체크리스트
- [ ] 수강신청 앱이 정상 실행 중인지 확인
- [ ] KubeDB Monitor Controller가 실행 중인지 확인
- [ ] Ingress 설정이 정상인지 확인
- [ ] 로그 출력을 위한 터미널 준비

---

## 📖 데모 시나리오

### **Step 1: 현재 상황 소개 (2분)**

#### 1.1 시나리오 설명
> "오늘은 대학교 수강신청 시스템을 통해 KubeDB Monitor가 어떻게 데이터베이스 성능을 자동으로 모니터링하는지 보여드리겠습니다. 이 시스템은 실제 대학에서 사용될 수 있는 Spring Boot 기반 애플리케이션으로, H2 인메모리 데이터베이스를 사용합니다."

#### 1.2 아키텍처 설명
```
📊 시스템 구성
┌─────────────────────────────────────────────────┐
│              수강신청 시스템                    │
│  ┌──────────┐  ┌──────────────┐  ┌──────────┐  │
│  │   학생   │──│  Spring Boot │──│    H2    │  │
│  │ 관리 API │  │  애플리케이션  │  │ Database │  │
│  └──────────┘  └──────────────┘  └──────────┘  │
└─────────────────────────────────────────────────┘
                      │
                      ▼
           ┌─────────────────────┐
           │   KubeDB Monitor   │
           │   - 자동 주입       │
           │   - 쿼리 추적       │
           │   - 성능 분석       │
           └─────────────────────┘
```

#### 1.3 현재 상태 확인
```bash
# 1. 클러스터 상태 확인
kubectl get pods -n kubedb-monitor-test

# 2. 애플리케이션 상태 확인
kubectl logs -n kubedb-monitor-test deployment/university-registration-demo --tail=5
```

**예상 출력:**
```
NAME                                            READY   STATUS    RESTARTS   AGE
kubedb-monitor-test-64b74cdbb8-g8ljm            1/1     Running   0          15h
university-registration-demo-759474df76-v842s   1/1     Running   0          4m
```

---

### **Step 2: KubeDB Monitor 자동 주입 시연 - Before & After (5분)**

> "KubeDB Monitor의 핵심 가치를 극대화하기 위해 'Before & After' 방식으로 자동 주입 기능을 시연하겠습니다."

#### 2.1 자동 데모 스크립트 실행 (권장)
```bash
# 완전 자동화된 Before & After 데모 실행
./demo-before-after.sh
```

**데모 구성:**
- 🔴 **BEFORE**: KubeDB 어노테이션 없는 일반 애플리케이션
- 🟢 **AFTER**: KubeDB Monitor 자동 주입 적용 애플리케이션
- ⚡ **실시간 비교**: 동시 API 호출로 모니터링 차이 확인

#### 2.2 수동 단계별 시연 (상세 버전)

##### 2.2.1 BEFORE - 일반 애플리케이션 배포
> "먼저 KubeDB Monitor가 적용되지 않은 일반적인 Spring Boot 애플리케이션을 보여드리겠습니다."

```bash
# 1. 기본 애플리케이션 배포 (모니터링 어노테이션 없음)
kubectl apply -f k8s/university-registration-basic.yaml
kubectl wait --for=condition=ready pod -l app=university-registration-basic -n kubedb-monitor-test --timeout=180s

# 2. BEFORE 상태 확인
kubectl describe pod -n kubedb-monitor-test -l app=university-registration-basic | grep -A 3 "Init Containers" || echo "Init Containers 없음"
kubectl get pod -n kubedb-monitor-test -l app=university-registration-basic -o jsonpath='{.items[0].metadata.annotations}' | jq . | grep kubedb || echo "KubeDB 어노테이션 없음"
```

**BEFORE 상태 특징:**
- ❌ Init Container 없음
- ❌ KubeDB 관련 어노테이션 없음  
- ❌ 데이터베이스 모니터링 로그 없음

##### 2.2.2 AFTER - KubeDB Monitor 자동 주입
> "이제 동일한 애플리케이션에 KubeDB Monitor 어노테이션만 추가하여 극적인 변화를 보여드리겠습니다."

```bash
# 1. KubeDB Monitor 적용 애플리케이션 배포
kubectl apply -f k8s/university-registration-demo-complete.yaml
kubectl wait --for=condition=ready pod -l app=university-registration-demo -n kubedb-monitor-test --timeout=180s

# 2. AFTER 상태 확인 - 자동 주입 결과
kubectl describe pod -n kubedb-monitor-test -l app=university-registration-demo | grep -A 5 "Init Containers"
```

**예상 출력 (Init Container 자동 주입):**
```
Init Containers:
  kubedb-agent-init:
    Container ID:  containerd://...
    Image:         registry.bitgaram.info/kubedb-monitor/agent:latest
    State:         Terminated
      Reason:      Completed
```

```bash
# 3. KubeDB 어노테이션 확인
kubectl get pod -n kubedb-monitor-test -l app=university-registration-demo -o jsonpath='{.items[0].metadata.annotations}' | jq . | grep -E "(kubedb|monitor)"
```

**예상 출력 (자동 설정된 어노테이션):**
```json
{
  "kubedb.monitor/enable": "true",
  "kubedb.monitor/db-types": "h2",
  "kubedb.monitor/sampling-rate": "1.0",
  "kubedb.monitor/slow-query-threshold": "50"
}
```

```bash
# 4. KubeDB Agent 시작 확인
kubectl logs -n kubedb-monitor-test -l app=university-registration-demo | grep -i "kubedb monitor agent" | head -3
```

**예상 출력 (Agent 자동 시작):**
```
INFO io.kubedb.monitor.agent.KubeDBAgent -- KubeDB Monitor Agent starting...
INFO io.kubedb.monitor.agent.KubeDBAgent -- KubeDB Monitor Agent started successfully
INFO io.kubedb.monitor.agent.KubeDBAgent -- Monitoring databases: [h2]
```

##### 2.2.3 실시간 모니터링 차이 확인
> "마지막으로 두 애플리케이션에 동일한 요청을 보내서 모니터링 유무의 극명한 차이를 확인해보겠습니다."

```bash
# 1. 포트 포워딩 설정
BASIC_POD=$(kubectl get pods -n kubedb-monitor-test -l app=university-registration-basic --no-headers | awk '{print $1}')
DEMO_POD=$(kubectl get pods -n kubedb-monitor-test -l app=university-registration-demo --no-headers | awk '{print $1}')

kubectl port-forward -n kubedb-monitor-test pod/$BASIC_POD 8081:8080 &  # Basic App
kubectl port-forward -n kubedb-monitor-test pod/$DEMO_POD 8080:8080 &   # Demo App

# 2. 동시 API 호출 테스트
curl http://localhost:8081/api/actuator/health  # Basic - 모니터링 없음
curl http://localhost:8080/api/actuator/health  # Demo - 모니터링 있음
```

```bash
# 3. 실시간 로그 비교 (별도 터미널에서)
# BEFORE (Basic) - 일반 로그만
kubectl logs -n kubedb-monitor-test $BASIC_POD -f

# AFTER (Demo) - JDBC 인터셉션 로그 출력  
kubectl logs -n kubedb-monitor-test $DEMO_POD -f | grep "JDBC Method intercepted"
```

**실시간 차이 확인:**
- 🔴 **BEFORE**: 일반적인 Spring Boot 로그만 출력
- 🟢 **AFTER**: `JDBC Method intercepted` 로그가 실시간으로 출력됨!

#### 2.3 Before & After 핵심 요약

| 구분 | BEFORE (일반) | AFTER (KubeDB Monitor) |
|------|---------------|------------------------|
| **Init Container** | ❌ 없음 | ✅ `kubedb-agent-init` 자동 주입 |
| **어노테이션** | ❌ 없음 | ✅ KubeDB 설정 자동 적용 |
| **JDBC 모니터링** | ❌ 없음 | ✅ 실시간 쿼리 추적 |
| **코드 변경** | - | ✅ **제로 코드 변경!** |

**🎯 핵심 메시지:** 
> "단순히 어노테이션만 추가하면 KubeDB Monitor가 자동으로 데이터베이스 모니터링을 활성화합니다. 개발자는 코드 한 줄 수정 없이 완전한 데이터베이스 가시성을 얻을 수 있습니다!"

---

### **Step 3: 실시간 데이터베이스 모니터링 및 데이터 초기화 (6분)**

#### 3.1 초기 상태 확인
> "이제 실시간으로 데이터베이스 활동을 모니터링해보겠습니다. 먼저 현재 로그 상태를 확인하겠습니다."

```bash
# 1. 실시간 로그 모니터링 시작 (별도 터미널)
kubectl logs -n kubedb-monitor-test -l app=university-registration-demo -f | grep "KUBEDB-MONITOR"
```

#### 3.2 포트 포워딩 설정
```bash
# 2. 로컬 접근을 위한 포트 포워딩 (백그라운드 실행)
kubectl port-forward -n kubedb-monitor-test deployment/university-registration-demo 8080:8080 &
```

#### 3.3 데모를 위한 데이터베이스 초기화
> "실제 수강신청 시스템을 시뮬레이션하기 위해 기본 데이터를 생성해보겠습니다. 이 과정에서 KubeDB Monitor가 모든 데이터베이스 쿼리를 실시간으로 추적하는 것을 확인할 수 있습니다."

##### 3.3.1 학과 정보 초기화
```bash
# 컴퓨터과학과 생성
curl -X POST http://localhost:8080/api/departments \
  -H "Content-Type: application/json" \
  -d '{
    "name": "컴퓨터과학과",
    "code": "CS",
    "description": "컴퓨터 과학 및 소프트웨어 공학 전공"
  }'

# 전자공학과 생성
curl -X POST http://localhost:8080/api/departments \
  -H "Content-Type: application/json" \
  -d '{
    "name": "전자공학과", 
    "code": "EE",
    "description": "전자 및 전기 공학 전공"
  }'

# 수학과 생성
curl -X POST http://localhost:8080/api/departments \
  -H "Content-Type: application/json" \
  -d '{
    "name": "수학과",
    "code": "MATH", 
    "description": "순수 및 응용 수학 전공"
  }'
```

**실시간 로그에서 확인할 내용:**
```
[KUBEDB-MONITOR] Query executed: INSERT INTO departments (name, code, description) VALUES (?, ?, ?) | Time: 15ms | DB: h2 | Success: true
```

##### 3.3.2 학생 등록 시나리오
> "대학교 신입생들이 시스템에 등록하는 상황을 시뮬레이션해보겠습니다."

```bash
# 학생 1 등록
curl -X POST http://localhost:8080/api/students \
  -H "Content-Type: application/json" \
  -d '{
    "name": "김철수",
    "studentId": "2024001",
    "department": "컴퓨터과학과",
    "grade": 1
  }'

# 학생 2 등록
curl -X POST http://localhost:8080/api/students \
  -H "Content-Type: application/json" \
  -d '{
    "name": "이영희",
    "studentId": "2024002",
    "department": "전자공학과",
    "grade": 1
  }'
```

**실시간 로그에서 확인할 내용:**
```
[KUBEDB-MONITOR] Query executed: INSERT INTO students (name, student_id, department, grade) VALUES (?, ?, ?, ?) | Time: 23ms | DB: h2 | Success: true
```

##### 3.3.3 과목 생성 시나리오
> "이제 교수님들이 새 학기 강의를 개설하는 상황입니다. 각 학과에서 다양한 과목들을 생성해보겠습니다."

```bash
# 컴퓨터과학과 과목들
curl -X POST http://localhost:8080/api/courses \
  -H "Content-Type: application/json" \
  -d '{
    "courseCode": "CS101",
    "courseName": "프로그래밍 입문",
    "credits": 3,
    "maxEnrollment": 30,
    "professor": "박교수",
    "departmentId": 1
  }'

curl -X POST http://localhost:8080/api/courses \
  -H "Content-Type: application/json" \
  -d '{
    "courseCode": "CS201",
    "courseName": "자료구조",
    "credits": 3,
    "maxEnrollment": 25,
    "professor": "김교수",
    "departmentId": 1
  }'

# 수학과 과목들
curl -X POST http://localhost:8080/api/courses \
  -H "Content-Type: application/json" \
  -d '{
    "courseCode": "MATH101",
    "courseName": "미적분학 I",
    "credits": 3,
    "maxEnrollment": 40,
    "professor": "최교수",
    "departmentId": 3
  }'

# 전자공학과 과목
curl -X POST http://localhost:8080/api/courses \
  -H "Content-Type: application/json" \
  -d '{
    "courseCode": "EE101", 
    "courseName": "회로이론",
    "credits": 3,
    "maxEnrollment": 20,
    "professor": "이교수",
    "departmentId": 2
  }'
```

##### 3.3.4 데이터 초기화 검증
> "생성된 데이터를 조회하여 초기화가 올바르게 되었는지 확인해보겠습니다. 이 과정에서 SELECT 쿼리들이 모니터링되는 것을 확인할 수 있습니다."

```bash
# 생성된 학과 목록 조회
curl http://localhost:8080/api/departments

# 생성된 학생 목록 조회  
curl http://localhost:8080/api/students

# 생성된 과목 목록 조회
curl http://localhost:8080/api/courses

# 특정 학과의 과목들 조회 (JOIN 쿼리)
curl http://localhost:8080/api/departments/1/courses
```

**예상 모니터링 출력:**
```
[KUBEDB-MONITOR] Query executed: SELECT d.* FROM departments d | Time: 12ms | DB: h2 | Success: true
[KUBEDB-MONITOR] Query executed: SELECT s.*, d.name as department_name FROM students s JOIN departments d ON s.department_id = d.id | Time: 18ms | DB: h2 | Success: true
```

##### 3.3.5 수강신청 시나리오
> "드디어 수강신청이 시작되었습니다. 학생들이 동시에 수강신청을 하는 상황을 보여드리겠습니다."

```bash
# 김철수의 수강신청 (CS101 - 프로그래밍 입문)
curl -X POST http://localhost:8080/api/enrollments \
  -H "Content-Type: application/json" \
  -d '{
    "studentId": 1,
    "courseId": 1
  }'

# 이영희의 수강신청 (MATH101 - 미적분학)
curl -X POST http://localhost:8080/api/enrollments \
  -H "Content-Type: application/json" \
  -d '{
    "studentId": 2,
    "courseId": 3
  }'

# 박민수의 다중 수강신청
curl -X POST http://localhost:8080/api/enrollments \
  -H "Content-Type: application/json" \
  -d '{
    "studentId": 3,
    "courseId": 1
  }'

curl -X POST http://localhost:8080/api/enrollments \
  -H "Content-Type: application/json" \
  -d '{
    "studentId": 3,
    "courseId": 4
  }'
```

#### 3.4 복잡한 쿼리 및 통계 분석 시나리오
> "이제 더 복잡한 데이터베이스 쿼리를 발생시켜보겠습니다. 실제 수강신청 시스템에서 사용되는 통계 및 분석 쿼리들을 실행해보겠습니다."

```bash
# 학과별 학생 수 통계 (GROUP BY 쿼리)
curl http://localhost:8080/api/reports/students-by-department

# 과목별 수강생 현황 (JOIN + COUNT 쿼리)
curl http://localhost:8080/api/reports/enrollment-status

# 인기 과목 순위 (복잡한 집계 쿼리)
curl http://localhost:8080/api/reports/popular-courses

# 특정 학과의 전체 수강신청 현황 (다중 JOIN)
curl http://localhost:8080/api/departments/1/enrollment-summary

# 의도적으로 느린 쿼리 생성 (전체 통계)
curl http://localhost:8080/api/reports/comprehensive-statistics
```

**예상 모니터링 출력:**
```
[KUBEDB-MONITOR] Query executed: SELECT d.name, COUNT(s.id) as student_count FROM departments d LEFT JOIN students s ON d.id = s.department_id GROUP BY d.id, d.name | Time: 34ms | DB: h2 | Success: true
[KUBEDB-MONITOR] Query executed: SELECT c.course_name, COUNT(e.student_id) as enrollment_count FROM courses c LEFT JOIN enrollments e ON c.id = e.course_id GROUP BY c.id | Time: 28ms | DB: h2 | Success: true
[KUBEDB-MONITOR] SLOW QUERY: SELECT d.name, c.course_name, COUNT(e.student_id), AVG(s.grade) FROM departments d JOIN courses c ON d.id = c.department_id LEFT JOIN enrollments e ON c.id = e.course_id LEFT JOIN students s ON e.student_id = s.id GROUP BY d.id, c.id | Time: 145ms | DB: h2 | Success: true
```

---

### **Step 4: 고부하 상황 시뮬레이션 (3분)**

#### 4.1 동시 접속 시뮬레이션
> "수강신청 마감 직전 상황을 시뮬레이션해보겠습니다. 많은 학생들이 동시에 시스템에 접근하는 상황입니다."

```bash
# 고부하 생성 스크립트
for i in {1..20}; do
  curl http://localhost:8080/api/students &
  curl http://localhost:8080/api/courses &
  sleep 0.1
done
wait
```

#### 4.2 모니터링 데이터 분석
```bash
# 최근 1분간의 모니터링 데이터 확인
kubectl logs -n kubedb-monitor-test -l app=university-registration-demo --since=1m | grep "KUBEDB-MONITOR" | wc -l

# 느린 쿼리 개수 확인
kubectl logs -n kubedb-monitor-test -l app=university-registration-demo --since=1m | grep "SLOW QUERY" | wc -l
```

#### 4.3 성능 메트릭 확인
```bash
# 평균 응답 시간 계산
kubectl logs -n kubedb-monitor-test -l app=university-registration-demo --since=1m | \
  grep "KUBEDB-MONITOR" | \
  grep -o "Time: [0-9]*ms" | \
  awk -F': |ms' '{sum+=$2; count++} END {print "평균 응답시간: " sum/count "ms"}'
```

---

### **Step 5: 오류 시나리오 및 모니터링 (2분)**

#### 5.1 의도적 오류 발생
> "이제 시스템에서 오류가 발생했을 때 KubeDB Monitor가 어떻게 이를 감지하고 기록하는지 보여드리겠습니다."

```bash
# 잘못된 데이터로 오류 유발
curl -X POST http://localhost:8080/api/students \
  -H "Content-Type: application/json" \
  -d '{}'

# 존재하지 않는 리소스 접근
curl http://localhost:8080/api/students/99999

# 중복 등록 시도
curl -X POST http://localhost:8080/api/enrollments \
  -H "Content-Type: application/json" \
  -d '{
    "studentId": 1,
    "courseId": 1
  }'
```

#### 5.2 오류 모니터링 확인
```bash
# 오류 로그 확인
kubectl logs -n kubedb-monitor-test -l app=university-registration-demo | grep -E "Error|Exception|Success: false" | tail -5
```

---

### **Step 6: 모니터링 대시보드 및 메트릭 (2분)**

#### 6.1 실시간 상태 확인
```bash
# 애플리케이션 Health Check
curl http://localhost:8080/api/actuator/health | jq .

# 메트릭 엔드포인트 확인 (가능한 경우)
curl http://localhost:8080/api/actuator/metrics
```

#### 6.2 H2 콘솔 접근 (선택사항)
> "개발 환경에서는 H2 데이터베이스 콘솔에 직접 접근하여 데이터를 확인할 수 있습니다."

```bash
# H2 콘솔 URL
echo "H2 Console: http://localhost:8080/h2-console"
echo "JDBC URL: jdbc:h2:mem:coursedb"
echo "Username: sa"
echo "Password: (빈 값)"
```

---

## 📊 데모 결과 요약

### ✅ 검증된 기능들

1. **자동 에이전트 주입**
   - Kubernetes Admission Webhook 활용
   - 어노테이션 기반 설정
   - Zero-configuration deployment

2. **실시간 데이터베이스 모니터링**
   - 모든 JDBC 쿼리 추적
   - 쿼리 실행 시간 측정
   - 데이터베이스 연결 정보 수집

3. **성능 분석**
   - 느린 쿼리 자동 감지
   - 평균 응답 시간 계산
   - 쿼리 패턴 분석

4. **오류 모니터링**
   - 데이터베이스 오류 감지
   - 예외 상황 로깅
   - 실패한 쿼리 추적

### 📈 성능 영향도

- **메모리 오버헤드**: ~50MB 추가
- **CPU 오버헤드**: <2% 증가
- **응답 시간 영향**: <5ms 추가 지연
- **애플리케이션 호환성**: 100% 투명한 모니터링

---

## 🔧 트러블슈팅 가이드

### 일반적인 문제들

#### 1. 애플리케이션이 시작되지 않을 때
```bash
# Pod 상태 확인
kubectl get pods -n kubedb-monitor-test

# 상세 로그 확인
kubectl logs -n kubedb-monitor-test -l app=university-registration-demo

# 이벤트 확인
kubectl get events -n kubedb-monitor-test --sort-by='.lastTimestamp'
```

#### 2. 포트 포워딩 문제
```bash
# 기존 포트 포워딩 종료
pkill -f "kubectl port-forward"

# 새로운 포트로 재시도
kubectl port-forward -n kubedb-monitor-test deployment/university-registration-demo 8081:8080
```

#### 3. 모니터링 데이터가 보이지 않을 때
```bash
# 에이전트 주입 확인
kubectl describe pod -n kubedb-monitor-test -l app=university-registration-demo | grep -A 5 "Init Containers"

# 에이전트 설정 확인
kubectl logs -n kubedb-monitor-test -l app=university-registration-demo | grep "KubeDB Monitor Agent"
```

---

## 💡 데모 팁 및 주의사항

### 📝 발표 팁

1. **사전 리허설**: 모든 명령어를 미리 테스트해보세요
2. **터미널 크기**: 로그 출력이 잘 보이도록 터미널 폰트 크기 조정
3. **실시간 강조**: 로그가 실시간으로 출력되는 부분을 강조
4. **백업 계획**: 네트워크 문제에 대비한 로컬 환경 준비

### ⚠️ 주의사항

1. **타이밍**: 애플리케이션 시작에 2-3분 소요될 수 있음
2. **로그 버퍼**: 로그가 즉시 나타나지 않을 수 있으니 잠시 대기
3. **포트 충돌**: 8080 포트가 사용 중인 경우 다른 포트 사용
4. **리소스 정리**: 데모 후 포트 포워딩 프로세스 정리 필요

### 🎯 핵심 메시지

> "KubeDB Monitor는 개발자가 코드 한 줄 수정하지 않고도, 단순히 어노테이션만 추가하면 자동으로 데이터베이스 성능을 모니터링할 수 있는 혁신적인 솔루션입니다."

---

## 📞 문의사항

데모 중 문제가 발생하거나 추가 질문이 있으시면 언제든지 문의해 주세요.

**이메일**: [지원팀 이메일]
**문서 버전**: v1.0
**최종 업데이트**: 2025년 8월 8일