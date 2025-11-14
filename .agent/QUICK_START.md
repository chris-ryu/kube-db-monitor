# 🚀 AI 에이전트 문서 시스템 빠른 시작 가이드

## 5분 안에 시작하기

### 1단계: 중앙 인덱스 확인 (1분)

```bash
# 중앙 인덱스 파일 읽기
cat .agent/readme.md
```

**중요**: 이 파일은 모든 문서의 진입점입니다. 작업 시작 전 반드시 읽어야 합니다.

### 2단계: 상황에 맞는 문서 찾기 (2분)

| 상황 | 읽을 문서 |
|-----|----------|
| 프로젝트 처음 접근 | [architecture-overview.md](.agent/system/architecture-overview.md) |
| Agent 코드 수정 | [agent-modification-workflow.md](.agent/SOPs/agent-modification-workflow.md) |
| 시스템 배포 | [deployment-workflow.md](.agent/SOPs/deployment-workflow.md) |
| 새 이벤트 타입 추가 | [new-event-type-implementation.md](.agent/SOPs/new-event-type-implementation.md) |

### 3단계: `/update-doc` 커맨드 사용 (2분)

```bash
# 문서 시스템 초기화 (처음 사용 시)
/update-doc initialize

# 특정 주제 업데이트
/update-doc event-pipeline

# 새 SOP 생성
/update-doc sop my-new-workflow
```

**자세한 사용법**: [how-to-use-update-doc.md](.agent/how-to-use-update-doc.md)

## 핵심 원칙

### ✅ DO (해야 할 것)

1. **작업 전 문서 먼저 읽기**: `.agent/readme.md`에서 관련 문서 찾기
2. **SOP 준수**: Agent 수정, 배포 등은 반드시 SOP 따르기
3. **문서 업데이트**: 기능 구현 후 `/update-doc` 실행
4. **테스트 실행**: 변경 후 항상 종합 테스트 스위트 실행

### ❌ DON'T (하지 말아야 할 것)

1. **Fallback 코드 무단 추가 금지**: 안전모드, safe mode 등 사용자 동의 없이 추가 금지
2. **추측으로 작업 금지**: 불확실하면 문서 확인 또는 사용자에게 질문
3. **테스트 건너뛰기 금지**: 테스트 실패 시 배포 절대 금지
4. **문서 무시 금지**: 모든 작업은 문서 기반으로 진행

## 자주 사용하는 명령어

```bash
# 문서 확인
cat .agent/readme.md

# 문서 업데이트
/update-doc initialize
/update-doc [주제]
/update-doc sop [SOP-이름]

# Agent 테스트
./scripts/agent-comprehensive-test-suite.sh

# 이벤트 파이프라인 검증
./scripts/event-pipeline-verification-test.sh

# 배포
make build-and-deploy-all
```

## 문서 구조 한눈에 보기

```
.agent/
├── readme.md                          ⭐ 시작점
├── how-to-use-update-doc.md           📖 /update-doc 사용법
├── QUICK_START.md                     🚀 이 파일
├── system/                            📂 시스템 문서
│   ├── architecture-overview.md       🏗️ 아키텍처
│   ├── project-structure.md           📁 프로젝트 구조
│   └── event-pipeline.md              🔄 이벤트 파이프라인
├── SOPs/                              📋 표준 운영 절차
│   ├── agent-modification-workflow.md 🔧 Agent 수정
│   ├── deployment-workflow.md         🚀 배포
│   └── new-event-type-implementation.md 🆕 이벤트 타입 추가
└── task/                              📦 구현 계획
    ├── README.md                      📖 Task 가이드
    ├── completed/                     ✅ 완료된 작업
    └── templates/                     📝 템플릿
```

## 첫 작업 예시

### 예시 1: Agent 코드 수정

```bash
# 1. SOP 확인
cat .agent/SOPs/agent-modification-workflow.md

# 2. 코드 수정
vi kubedb-monitor-agent/src/main/java/io/kubedb/monitor/agent/UniversalJDBCInterceptor.java

# 3. 빌드 및 테스트
cd kubedb-monitor-agent
mvn clean test

# 4. 종합 테스트
cd ..
./scripts/agent-comprehensive-test-suite.sh

# 5. 문서 업데이트
/update-doc agent-architecture

# 6. 배포
make build-and-deploy-agent
```

### 예시 2: 새로운 이벤트 타입 추가

```bash
# 1. SOP 확인
cat .agent/SOPs/new-event-type-implementation.md

# 2. 이벤트 설계 (문서 작성)
vi .agent/task/2025-10-08-new-event-type.md

# 3. Agent 구현
# ... (Agent 코드 수정)

# 4. Control Plane 구현
# ... (Control Plane 코드 수정)

# 5. Dashboard 구현
# ... (Dashboard 코드 수정)

# 6. 이벤트 파이프라인 검증
./scripts/event-pipeline-verification-test.sh

# 7. 문서 업데이트
/update-doc event-pipeline

# 8. 배포
make build-and-deploy-all
```

## 도움이 필요할 때

1. **문서 시스템 사용법**: [how-to-use-update-doc.md](.agent/how-to-use-update-doc.md)
2. **전체 가이드**: [readme.md](.agent/readme.md)
3. **프로젝트 메모리**: [CLAUDE.md](../CLAUDE.md)
4. **프로젝트 개요**: [README.md](../README.md)

## 다음 단계

1. ✅ [.agent/readme.md](.agent/readme.md) 전체 읽기 (10분)
2. ✅ [architecture-overview.md](.agent/system/architecture-overview.md) 읽기 (15분)
3. ✅ 자주 사용할 SOP 파악 및 숙지 (20분)
4. ✅ `/update-doc` 커맨드 테스트 (5분)

**총 예상 시간**: 약 50분

---

**환영합니다!** 🎉

이제 KubeDB Monitor 프로젝트의 AI 에이전트 기반 문서주도 워크플로우를 사용할 준비가 되었습니다.

문서를 활용하여 더 일관되고 효율적으로 작업하세요!
