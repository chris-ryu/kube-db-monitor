# `/update-doc` 커맨드 사용 가이드

## 개요

`/update-doc` 커맨드는 KubeDB Monitor 프로젝트의 AI 에이전트 문서 시스템을 유지보수하는 슬래시 커맨드입니다.

## 설치 확인

커맨드가 정상적으로 설치되었는지 확인:

```bash
ls -la .claude/commands/update-doc.md
```

예상 결과: 파일이 존재해야 함

## 사용법

### 1. 문서 시스템 초기화

프로젝트를 처음 시작하거나 대규모 리팩토링 후 전체 문서를 재생성:

```bash
/update-doc initialize
```

**수행 작업**:
- 프로젝트 전체 스캔
- System 문서 재생성 (architecture-overview.md, project-structure.md, event-pipeline.md)
- SOPs 검증
- Task 아카이브 정리
- .agent/readme.md 업데이트
- CLAUDE.md 동기화

**예상 소요 시간**: 5-10분

### 2. 특정 주제 문서 업데이트

특정 컴포넌트나 기능 관련 문서만 업데이트:

```bash
/update-doc [주제명]
```

**예시**:

```bash
# 이벤트 파이프라인 문서 업데이트
/update-doc event-pipeline

# Agent 아키텍처 문서 업데이트
/update-doc agent-architecture

# 배포 관련 문서 업데이트
/update-doc deployment
```

**수행 작업**:
- Git 변경 이력 분석
- 관련 문서 식별 및 업데이트
- 링크 및 참조 검증
- .agent/readme.md 업데이트

**예상 소요 시간**: 2-5분

### 3. 새로운 SOP 생성

반복적인 작업이나 에이전트의 실수를 방지하기 위한 표준 운영 절차 생성:

```bash
/update-doc sop [SOP-이름]
```

**예시**:

```bash
# Connection Pool 모니터링 설정 SOP
/update-doc sop connection-pool-monitoring-setup

# 데드락 디버깅 SOP
/update-doc sop deadlock-debugging-workflow

# 새 대시보드 컴포넌트 추가 SOP
/update-doc sop add-dashboard-component
```

**수행 작업**:
- SOP 범위 및 구조 설계
- `.agent/SOPs/[sop-name].md` 파일 생성
- 단계별 워크플로우 작성
- 체크리스트 및 트러블슈팅 가이드 포함
- .agent/readme.md에 링크 추가

**예상 소요 시간**: 5-10분

## 실제 사용 시나리오

### 시나리오 1: 새로운 이벤트 타입 추가 후

**상황**: `connection_leak_detected` 이벤트 타입을 추가했고, Agent, Control Plane, Dashboard를 모두 수정함

**명령어**:
```bash
/update-doc event-pipeline
```

**결과**:
- `.agent/system/event-pipeline.md`에 새 이벤트 타입 문서화
- 지원 이벤트 타입 목록 업데이트 (5개 → 6개)
- 이벤트 생성 로직 및 스키마 설명 추가
- 관련 링크 업데이트

### 시나리오 2: Agent 수정 중 동일한 실수 반복

**상황**: Agent JAR 경로를 잘못 설정하는 실수를 여러 번 반복함

**명령어**:
```bash
/update-doc sop agent-jar-path-verification
```

**결과**:
- `.agent/SOPs/agent-jar-path-verification.md` 생성
- 올바른 Agent JAR 경로 명시
- 잘못된 경로 사례 및 해결 방법 포함
- 배포 전 검증 체크리스트 추가
- `.agent/readme.md`의 "상황별 문서 찾기" 섹션에 추가

### 시나리오 3: 프로젝트 구조 대규모 변경 후

**상황**: 새로운 모듈 추가, 디렉토리 재구성 등 프로젝트 구조가 크게 변경됨

**명령어**:
```bash
/update-doc initialize
```

**결과**:
- 전체 문서 시스템 재스캔
- `.agent/system/project-structure.md` 완전 재작성
- `.agent/system/architecture-overview.md` 업데이트
- 모든 문서의 파일 경로 및 링크 검증
- `.agent/readme.md` 최신화

### 시나리오 4: 배포 절차 개선 후

**상황**: Makefile 기반 배포를 개선하여 새로운 명령어 추가

**명령어**:
```bash
/update-doc deployment
```

**결과**:
- `.agent/SOPs/deployment-workflow.md` 업데이트
- 새로운 Makefile 명령어 설명 추가
- 배포 단계 수정
- 체크리스트 업데이트

## 사용 팁

### 팁 1: 언제 사용해야 하나?

**즉시 사용**:
- ✅ 주요 기능 구현 완료 후
- ✅ 아키텍처 변경 후
- ✅ 에이전트의 반복적인 실수를 수정한 후
- ✅ 새로운 컴포넌트 추가 후

**나중에 사용 가능**:
- 🟡 사소한 버그 수정
- 🟡 코드 스타일 변경
- 🟡 주석 추가

### 팁 2: initialize vs 일반 업데이트

| 상황 | 사용 명령어 | 이유 |
|-----|-----------|------|
| 프로젝트 시작 | `initialize` | 문서 시스템 첫 구축 |
| 대규모 리팩토링 후 | `initialize` | 구조 전체 변경 |
| 기능 추가 | 일반 업데이트 | 특정 부분만 변경 |
| 버그 수정 | 일반 업데이트 또는 SOP | 관련 문서만 수정 |

### 팁 3: SOP 생성 기준

**SOP를 생성해야 하는 경우**:
1. 동일한 작업을 3번 이상 수행할 때
2. 에이전트가 동일한 실수를 2번 이상 반복할 때
3. 복잡한 다단계 프로세스일 때
4. 새 팀원 온보딩 시 필요한 절차일 때

**SOP가 불필요한 경우**:
- 일회성 작업
- 자명한 간단한 작업
- 이미 기존 SOP에 포함된 작업

### 팁 4: 문서 업데이트 주기

**권장 주기**:
- **매 PR 전**: 해당 PR의 변경 사항을 문서에 반영
- **매 스프린트 종료 시**: 전체 문서 일관성 검증
- **분기별**: `initialize` 실행하여 전체 재정비

## 트러블슈팅

### 문제 1: `/update-doc` 명령어가 인식되지 않음

**원인**: 슬래시 커맨드 파일이 없거나 잘못된 위치

**해결**:
```bash
# 파일 존재 여부 확인
ls -la .claude/commands/update-doc.md

# 없으면 재생성
# (CLAUDE.md의 지침에 따라 생성)
```

### 문제 2: 문서 업데이트 후 링크가 깨짐

**원인**: 파일 경로 변경 또는 오타

**해결**:
```bash
# 깨진 링크 찾기
grep -r "\[.*\](.*.md)" .agent/

# 각 링크의 대상 파일 존재 여부 수동 확인
```

### 문제 3: CLAUDE.md와 .agent/readme.md가 불일치

**원인**: 한쪽만 업데이트되어 규칙 충돌

**해결**:
```bash
# 초기화로 동기화
/update-doc initialize
```

## 고급 사용

### 여러 주제 동시 업데이트

여러 컴포넌트를 동시에 수정한 경우:

```bash
# 방법 1: initialize 사용 (전체 재생성)
/update-doc initialize

# 방법 2: 순차적으로 업데이트
/update-doc agent-architecture
/update-doc event-pipeline
/update-doc deployment
```

### SOP 템플릿 커스터마이징

프로젝트에 특화된 SOP 템플릿이 필요한 경우:

1. `.agent/task/templates/` 디렉토리에 새 템플릿 추가
2. `/update-doc` 명령어 파일에서 해당 템플릿 참조하도록 수정

### 문서 버전 관리

Git을 사용하여 문서 변경 이력 추적:

```bash
# 문서 변경 이력 확인
git log --oneline .agent/

# 특정 문서의 변경 이력
git log -p .agent/system/architecture-overview.md

# 이전 버전으로 롤백
git checkout <commit-hash> .agent/system/architecture-overview.md
```

## 관련 문서

- [.agent/readme.md](./readme.md): 중앙 인덱스
- [CLAUDE.md](../CLAUDE.md): AI 에이전트 메모리 파일
- `.claude/commands/update-doc.md`: 실제 커맨드 정의 파일

## FAQ

**Q: `/update-doc`을 실행하면 기존 문서가 덮어써지나요?**

A: `initialize` 모드는 전체 재생성하지만, 일반 업데이트 모드는 변경 사항만 반영하여 기존 내용을 유지합니다.

**Q: 사용자가 직접 문서를 수정해도 되나요?**

A: 네, 가능합니다. `/update-doc`은 도구일 뿐이며, 수동 편집도 언제든지 가능합니다. 다만, 다음 번 `/update-doc` 실행 시 수동 변경 내용을 보존하려면 주의해야 합니다.

**Q: SOP를 삭제하려면?**

A: 해당 SOP 파일을 삭제하고 `.agent/readme.md`에서 링크를 제거하세요. 그 후 `/update-doc initialize`를 실행하여 정리하세요.

**Q: 영어로 문서를 작성해야 하나요?**

A: 아니요, 모든 문서는 한글로 작성해야 합니다. (코드 및 명령어 제외)

---

**마지막 업데이트**: 2025-10-08
