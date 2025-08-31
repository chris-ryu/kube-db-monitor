# 🎓 University Registration Performance Test

> Azure Load Testing을 활용한 KubeDB Monitor 데모용 성능 테스트 환경

## 📋 개요

이 프로젝트는 **KubeDB Monitor의 실시간 DB 모니터링 기능**을 시연하기 위한 수강신청 시스템 성능 테스트 환경입니다. 
실제 대학교 수강신청 상황을 시뮬레이션하여 데이터베이스 성능 모니터링의 필요성과 효과를 보여줍니다.

## 🎯 데모 목적

- ✅ **실시간 DB 모니터링**: 부하 증가에 따른 DB 성능 변화 실시간 감지
- ✅ **자동 알림 기능**: 성능 임계값 초과 시 즉시 알림 발송
- ✅ **쿼리 분석**: 느린 쿼리 및 병목점 자동 탐지
- ✅ **데드락 감지**: 동시성 문제 실시간 모니터링
- ✅ **성능 최적화**: 데이터 기반 DB 튜닝 제안

## 📁 파일 구조

```
performance-tests/
├── README.md                           # 이 파일
├── university-load-test.jmx            # JMeter 테스트 스크립트
├── azure-load-test.yaml               # Azure Load Testing 설정
├── run-demo.sh                         # 자동 데모 실행 스크립트
├── test-data/                          # 테스트 데이터
│   ├── students.csv                    # 가상 학생 정보 (100명)
│   ├── courses.csv                     # 과목 정보 (50개)
│   └── scenarios.csv                   # 테스트 시나리오 정의
├── results/                            # 테스트 결과 저장소 (자동 생성)
└── logs/                              # 테스트 로그 저장소 (자동 생성)
```

## 🚀 빠른 시작 (1분 만에 데모 시작)

### 1. 환경 확인
```bash
# 수강신청 시스템 상태 확인
curl https://university-registration.bitgaram.info/api/courses

# KubeDB Monitor 대시보드 접속
open https://kube-db-mon-dashboard.bitgaram.info
```

### 2. 자동 데모 실행
```bash
cd performance-tests
./run-demo-fixed.sh
```

### 3. 개별 시나리오 실행
```bash
# 평상시 테스트 (5명, 3회 반복)
./run-demo-fixed.sh warmup

# 수강신청 러시 (20명, 5회 반복)  
./run-demo-fixed.sh peak

# 극한 부하 테스트 (50명, 3회 반복)
./run-demo-fixed.sh stress
```

## 📊 테스트 시나리오

### 🔥 Phase 1: Warm-up (평상시)
- **동시 사용자**: 5명
- **반복 횟수**: 3회
- **램프업**: 5초
- **목적**: 시스템 정상 동작 확인
- **예상 결과**: 응답시간 < 1초, 에러율 < 10%

### ⚡ Phase 2: Peak Time (수강신청 러시)
- **동시 사용자**: 20명  
- **반복 횟수**: 5회
- **램프업**: 10초
- **목적**: 실제 수강신청 상황 재현
- **예상 결과**: 응답시간 1-3초, 일부 대기 발생

### 🚨 Phase 3: Stress Test (극한 부하)
- **동시 사용자**: 50명
- **반복 횟수**: 3회
- **램프업**: 15초
- **목적**: 시스템 한계점 및 장애 상황 확인
- **예상 결과**: 응답시간 > 5초, 에러율 10-30%

## 🎭 사용자 행동 시뮬레이션

테스트는 실제 학생들의 수강신청 패턴을 모방합니다:

### 📚 과목 검색 및 조회 (30%)
- 과목 검색 (페이징, 필터링)
- 과목 상세 정보 조회
- 정원 및 시간표 확인

### 🛒 장바구니 관리 (40%)
- 장바구니 조회
- 과목 추가/제거
- 시간표 충돌 검증

### 📝 수강신청 (30%) ⭐ **핵심 테스트**
- 장바구니에서 일괄 신청
- 동시성 제어 테스트
- 트랜잭션 경쟁 상황 재현

## 🖥️ 실시간 모니터링

### KubeDB Monitor Dashboard
🔗 **URL**: https://kube-db-mon-dashboard.bitgaram.info

### 주요 관찰 포인트
- 📈 **DB Connection Pool**: 사용률 변화 추이
- ⏱️ **Query Response Time**: 쿼리 성능 실시간 분석
- 🔒 **Lock Contention**: 트랜잭션 경쟁 상황
- ⚠️ **Deadlock Detection**: 데드락 자동 감지
- 🐌 **Slow Query**: 임계값 초과 쿼리 탐지

## 🔧 Azure Load Testing 설정

### Azure Portal에서 실행
1. Azure Portal → "Load Testing" 검색
2. "Create" → 리소스 생성
3. 설정 파일 업로드: `azure-load-test.yaml`
4. 테스트 스크립트 업로드: `university-load-test.jmx`  
5. CSV 데이터 파일들 업로드
6. 테스트 실행

### Azure CLI로 실행
```bash
# Azure CLI 로그인
az login

# Load Testing 리소스 생성
az load create \
  --resource-group kubedb-monitor-demo \
  --name university-load-test \
  --location koreacentral

# 테스트 업로드 및 실행  
az load test create \
  --test-id university-registration-test \
  --load-test-resource university-load-test \
  --resource-group kubedb-monitor-demo \
  --test-plan university-load-test.jmx \
  --env-vars BASE_URL=https://university-registration.bitgaram.info
```

## 📈 결과 분석 및 해석

### ✅ 성공 지표
- **응답 시간**: 평균 < 2초, 95% < 5초
- **처리량**: > 100 TPS 유지  
- **에러율**: < 5%
- **DB 성능**: 커넥션 풀 < 80% 사용률

### ⚠️ 주의 지표
- **응답 시간**: 평균 > 5초
- **에러율**: > 10%
- **데드락**: 분당 > 5회 발생
- **커넥션 풀**: > 90% 사용률

### 🚨 위험 지표  
- **응답 시간**: > 10초
- **에러율**: > 20%
- **시스템 다운**: 503 에러 연속 발생
- **DB 연결 불가**: Connection timeout

## 🎬 데모 진행 가이드

### 1. 사전 준비 (5분)
```bash
# 환경 확인
./run-demo.sh warmup

# 대시보드 확인
open https://kube-db-mon-dashboard.bitgaram.info
```

### 2. 데모 실행 (10분)
- **0-2분**: Warm-up 단계 - "정상 상태를 보여드리겠습니다"
- **2-7분**: Peak Time - "수강신청 러시 상황입니다"  
- **7-10분**: Stress Test - "극한 상황에서 시스템이 어떻게 대응하는지 보세요"

### 3. 핵심 데모 포인트
- 📊 **실시간 감지**: "부하가 증가하자마자 즉시 감지됩니다"
- 🔔 **자동 알림**: "임계값을 넘으면 바로 알림이 옵니다"  
- 🔍 **상세 분석**: "어떤 쿼리가 문제인지 정확히 알 수 있습니다"
- 📈 **트렌드 분석**: "패턴을 보고 미리 대비할 수 있습니다"

## 🛠️ 커스터마이징

### 사용자 수 조정
```bash
# JMeter GUI로 열기
jmeter -t university-load-test.jmx

# 또는 명령행에서 직접 설정
jmeter -n -t university-load-test.jmx -Jusers=100 -Jduration=300
```

### 테스트 데이터 수정
- `test-data/students.csv`: 학생 정보 추가/수정
- `test-data/courses.csv`: 과목 정보 추가/수정
- `test-data/scenarios.csv`: 시나리오 조정

### 새로운 시나리오 추가
`university-load-test.jmx` 파일을 JMeter GUI로 열어서 수정:
1. Thread Group 복사
2. 시나리오별 설정 조정
3. 새로운 HTTP Request 추가

## 🔍 트러블슈팅

### 문제 1: 테스트가 시작되지 않음
```bash
# 서비스 상태 확인
curl -I https://university-registration.bitgaram.info/api/courses

# 로그 확인  
tail -f logs/*.log
```

### 문제 2: 높은 에러율
```bash
# 상세 에러 로그 확인
grep "ERROR" logs/*.log

# 시스템 리소스 확인
kubectl top pods -n kubedb-monitor-test
```

### 문제 3: 느린 응답 시간
- KubeDB Monitor에서 Slow Query 확인
- DB 커넥션 풀 상태 점검
- 인덱스 사용 현황 분석

## 📞 지원 및 문의

- **GitHub**: https://github.com/kubedb-monitor/kubedb-monitor
- **문서**: https://docs.kubedb-monitor.com  
- **이슈 제보**: https://github.com/kubedb-monitor/kubedb-monitor/issues

## 📝 라이선스

이 프로젝트는 MIT 라이선스 하에 제공됩니다.