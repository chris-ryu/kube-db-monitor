# Task 아카이브

이 디렉토리는 구현 계획서(PRD: Product Requirement Document)와 기술 설계 문서를 저장하는 아카이브입니다.

## 목적

- **지식 보존**: 구현된 기능의 의사결정 과정과 설계 배경을 기록
- **재사용성**: 유사한 기능 구현 시 참고 자료로 활용
- **온보딩**: 새로운 개발자가 프로젝트 히스토리를 이해하는 데 도움

## 디렉토리 구조

```
task/
├── README.md                           # 이 파일
├── completed/                          # 완료된 작업
│   ├── 2025-08-30-bytebuddy-agent-implementation.md
│   ├── 2025-09-01-connection-pool-monitoring.md
│   └── ...
└── templates/                          # 템플릿
    ├── feature-prd-template.md
    └── technical-design-template.md
```

## 사용 방법

### 1. 새로운 기능 구획 시

1. **계획 모드 사용**: AI 에이전트의 "계획 모드(plan mode)"를 활용하여 구현 계획서 생성
2. **파일 생성**: `task/` 디렉토리에 날짜와 기능명을 포함한 파일명으로 저장
   - 예: `2025-10-08-session-recording-feature.md`
3. **내용 구성**:
   - 기능 개요
   - 요구사항
   - 기술 설계
   - 구현 단계
   - 테스트 계획

### 2. 구현 완료 후

1. **파일 이동**: 구현이 완료되면 `task/completed/` 디렉토리로 이동
2. **결과 추가**: 문서 하단에 "구현 결과" 섹션 추가
   - 실제 구현 내역
   - 발생한 문제 및 해결 방법
   - 배운 점

### 3. 유사 기능 구현 시

1. **아카이브 검색**: 기존 문서에서 유사한 패턴이나 접근 방식 검색
2. **재사용**: 검증된 설계 패턴과 구현 방법 재활용
3. **개선**: 이전 문제점을 개선한 새로운 구현

## 파일 네이밍 규칙

```
YYYY-MM-DD-feature-name.md
```

**예시**:
- `2025-08-30-bytebuddy-agent-implementation.md`
- `2025-09-01-connection-pool-monitoring.md`
- `2025-09-08-session-recording-feature.md`

## 템플릿

### Feature PRD Template

새로운 기능 구현 시 사용하는 템플릿입니다.

[templates/feature-prd-template.md](./templates/feature-prd-template.md) 참조

### Technical Design Template

기술 설계 문서 작성 시 사용하는 템플릿입니다.

[templates/technical-design-template.md](./templates/technical-design-template.md) 참조

## 완료된 주요 작업 목록

### 2025년 8월

- **ByteBuddy Agent 구현** (2025-08-30)
  - ASM 기반에서 ByteBuddy 기반으로 마이그레이션
  - PostgreSQL 호환성 완전 해결
  - [문서](./completed/2025-08-30-bytebuddy-agent-implementation.md)

- **Connection Pool 모니터링** (2025-09-01)
  - HikariCP, Tomcat JDBC 등 주요 Connection Pool 지원
  - 실시간 메트릭 수집 및 Dashboard 표시
  - [문서](./completed/2025-09-01-connection-pool-monitoring.md)

### 2025년 9월

- **Session 녹화 기능** (2025-09-08)
  - 메트릭 이벤트 녹화 및 재생 기능
  - Settings 페이지 추가 (계정 및 보안 관리)
  - [문서](./completed/2025-09-08-session-recording-feature.md)

## 관련 문서

- [System Architecture Overview](../system/architecture-overview.md)
- [Project Structure](../system/project-structure.md)
- [Agent Modification Workflow](../SOPs/agent-modification-workflow.md)
- [New Event Type Implementation](../SOPs/new-event-type-implementation.md)

## 버전 히스토리

- **v1.0** (2025-10-08): 초기 Task 아카이브 디렉토리 구조 생성
