# 📊 University Registration 성능 테스트 데모 가이드

> **🎯 목적**: KubeDB Monitor의 실시간 DB 모니터링 기능을 Azure Load Testing으로 시연하기 위한 완전 가이드

## 🚀 빠른 시작 (5분 만에 데모 준비하기)

### 1. **환경 확인**
```bash
# 수강신청 시스템이 실행 중인지 확인
curl https://university-registration.bitgaram.info/api/courses

# KubeDB Monitor 대시보드 접속 확인  
open https://kube-db-mon-dashboard.bitgaram.info
```

### 2. **테스트 파일 준비**
```bash
cd /Users/narzis/workspace/kube-db-monitor
ls performance-tests/
# 다음 파일들이 있어야 합니다:
# - university-load-test.jmx
# - test-data/students.csv
# - test-data/courses.csv
# - azure-load-test.yaml
```

---

## 📖 상세 데모 시나리오

### 🎭 **데모 스토리**
"대학교 수강신청 첫날, 1000명의 학생이 동시에 몰리는 상황을 시뮬레이션합니다!"

### **Phase 1: 평상시 (Warm-up) - 2분**
- 👥 **동시 사용자**: 50명
- ⏱️ **시간**: 2분
- 🎯 **목적**: 시스템 정상 동작 확인
- 📊 **예상 결과**: 응답시간 < 1초, 에러율 0%

```
💡 이 단계에서는 시스템이 안정적으로 동작하는 모습을 보여줍니다.
KubeDB Monitor에서 정상적인 DB 메트릭을 확인할 수 있습니다.
```

### **Phase 2: 수강신청 러시 (Peak Time) - 5분**
- 👥 **동시 사용자**: 200명
- ⏱️ **시간**: 5분  
- 🎯 **목적**: 실제 수강신청 상황 재현
- 📊 **예상 결과**: 응답시간 1-3초, 일부 대기 발생

```
⚡ 이 단계에서는 다음을 관찰할 수 있습니다:
- DB 커넥션 풀 사용률 증가
- 쿼리 실행 시간 증가  
- 트랜잭션 경쟁 상황 발생
- 일부 타임아웃 발생 가능
```

### **Phase 3: 극한 부하 (Stress Test) - 3분**
- 👥 **동시 사용자**: 500명
- ⏱️ **시간**: 3분
- 🎯 **목적**: 시스템 한계점 확인
- 📊 **예상 결과**: 응답시간 > 5초, 에러율 10-20%

```
🔥 이 단계에서는 다음을 확인할 수 있습니다:
- 데드락 감지 및 해결
- 느린 쿼리 탐지
- 시스템 리소스 한계
- 자동 복구 메커니즘
```

---

## 🖥️ Azure Load Testing 설정 방법

### **Step 1: Azure Portal 접속**
1. https://portal.azure.com 접속
2. "Azure Load Testing" 검색
3. "Create Load Testing Resource" 클릭

### **Step 2: 기본 설정**
```yaml
Resource Group: kubedb-monitor-demo
Name: university-registration-load-test
Region: Korea Central
```

### **Step 3: 테스트 생성**
1. "Upload a JMeter script" 선택
2. `university-load-test.jmx` 업로드
3. CSV 파일들 업로드:
   - `students.csv`
   - `courses.csv`
   - `scenarios.csv`

### **Step 4: 테스트 구성**
```yaml
Test Plan:
  Name: University Registration Demo
  Description: KubeDB Monitor 데모용 수강신청 부하 테스트
  
Load Configuration:
  Engine Instances: 2
  Users per Engine: 250  
  Total Virtual Users: 500
  Test Duration: 10 minutes
  Ramp-up Time: 30 seconds
```

---

## 📊 실시간 모니터링 화면 구성

### **화면 1: Azure Load Testing Dashboard**
- 실시간 사용자 수
- 응답 시간 그래프
- 처리량 (TPS)
- 에러율

### **화면 2: KubeDB Monitor Dashboard**  
- DB 커넥션 현황
- 쿼리 성능 분석
- 트랜잭션 상태
- 데드락 감지

---

## 🎬 데모 실행 스크립트

### **자동 실행 (권장)**
```bash
# 데모 환경 자동 준비
./performance-tests/run-demo.sh

# 또는 수동으로 각 단계 실행:
./performance-tests/phase1-warmup.sh
./performance-tests/phase2-peak.sh  
./performance-tests/phase3-stress.sh
```

### **수동 실행 (상세 제어)**
```bash
# Phase 1: Warm-up (50 users, 2분)
jmeter -n -t university-load-test.jmx \
  -Jusers=50 -Jduration=120 \
  -Jrampup=30 -Jscenario=warmup

# Phase 2: Peak Time (200 users, 5분)  
jmeter -n -t university-load-test.jmx \
  -Jusers=200 -Jduration=300 \
  -Jrampup=60 -Jscenario=peak

# Phase 3: Stress Test (500 users, 3분)
jmeter -n -t university-load-test.jmx \
  -Jusers=500 -Jduration=180 \
  -Jrampup=30 -Jscenario=stress
```

---

## 🔍 결과 분석 및 해석

### **✅ 성공 지표**
- **응답 시간**: 평균 < 2초, 95% < 5초
- **처리량**: > 100 TPS 유지
- **에러율**: < 5%
- **DB 성능**: 커넥션 풀 < 80% 사용률

### **⚠️ 주의 지표**  
- **응답 시간**: 평균 > 5초
- **에러율**: > 10%
- **데드락**: 분당 > 5회 발생
- **커넥션 풀**: > 90% 사용률

### **🚨 위험 지표**
- **응답 시간**: > 10초
- **에러율**: > 20%  
- **시스템 다운**: 503 에러 연속 발생
- **DB 연결 불가**: Connection timeout

---

## 🎯 데모 포인트 & 강조사항

### **1. 실시간 감지 능력**
```
"보세요! 부하가 증가하자마자 KubeDB Monitor가 
즉시 느린 쿼리와 커넥션 증가를 감지했습니다!"
```

### **2. 자동 알림 기능**
```
"데드락이 발생하자마자 자동으로 알림이 왔네요. 
이제 개발자가 즉시 대응할 수 있습니다!"
```

### **3. 성능 분석**
```
"어떤 쿼리가 가장 느린지, 어떤 테이블에 락이 
많이 걸리는지 한눈에 볼 수 있습니다!"
```

### **4. 트렌드 분석**
```
"시간대별 패턴을 보면 수강신청 피크 타임을 
정확히 예측할 수 있겠네요!"
```

---

## 🛠️ 트러블슈팅

### **문제 1: 테스트가 시작되지 않음**
```bash
# 해결방법
kubectl get pods -n kubedb-monitor-test
kubectl logs university-registration-demo-xxx
```

### **문제 2: 응답 시간이 너무 느림**
```bash
# 데이터베이스 상태 확인
kubectl exec -it postgres-cluster-1-xxx -n postgres-system -- psql
SELECT * FROM pg_stat_activity;
```

### **문제 3: 에러율이 너무 높음**  
```bash
# 로그 확인
kubectl logs university-registration-demo-xxx -n kubedb-monitor-test --tail=100
```

---

## 📞 데모 중 Q&A 준비

### **Q: 실제 운영환경에서도 이렇게 모니터링이 가능한가요?**
A: 네! KubeDB Monitor는 ByteBuddy Agent를 통해 애플리케이션 코드 수정 없이 실시간 모니터링이 가능합니다.

### **Q: 알림 기능은 어떻게 설정하나요?**
A: Slack, Teams, Email 등 다양한 채널로 임계값 기반 자동 알림을 설정할 수 있습니다.

### **Q: 다른 데이터베이스도 지원하나요?**  
A: 현재 PostgreSQL을 완벽 지원하며, MySQL, Oracle 등도 확장 예정입니다.

---

## 🎉 데모 마무리

### **요약 포인트**
1. ✅ **쉬운 설치**: Kubernetes annotation만으로 모니터링 활성화
2. ✅ **실시간 감지**: 성능 이슈를 즉시 탐지하고 알림  
3. ✅ **상세 분석**: 쿼리 레벨까지 세밀한 성능 분석
4. ✅ **운영 최적화**: 데이터 기반의 성능 튜닝 가능

### **다음 단계**
- KubeDB Monitor 도입 검토
- POC 환경 구축 지원
- 운영팀 교육 및 지원

---

*🔗 관련 링크*
- [KubeDB Monitor GitHub](https://github.com/kubedb-monitor/kubedb-monitor)
- [기술 문서](https://docs.kubedb-monitor.com)  
- [데모 비디오](https://youtube.com/kubedb-monitor-demo)