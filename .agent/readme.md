# KubeDB Monitor AI Agent 문서 시스템

> **AI 에이전트를 위한 중앙 라우팅 테이블 및 지식 베이스**

이 문서는 AI 에이전트가 프로젝트를 이해하고 작업을 수행하는 데 필요한 모든 정보의 출발점입니다.

## 🎯 AI 에이전트 사용 가이드

### 작업 시작 전 필수 규칙

**⚠️ 모든 작업 시작 전 이 문서를 먼저 읽어야 합니다.**

### `/update-doc` 커맨드

이 문서 시스템을 유지보수하기 위한 슬래시 커맨드입니다.

```bash
/update-doc initialize           # 문서 시스템 초기화
/update-doc [주제]               # 특정 주제 문서 업데이트
/update-doc sop [SOP-이름]       # 새로운 SOP 생성
```

**자세한 사용법**: [how-to-use-update-doc.md](./how-to-use-update-doc.md)

**언제 사용하는가**:
- ✅ 주요 기능 구현 완료 후
- ✅ 아키텍처 변경 후
- ✅ 에이전트의 반복적인 실수를 수정한 후
- ✅ 새로운 컴포넌트 추가 후

### 필수 문서 참조 규칙

1. **프로젝트 이해가 필요할 때**:
   - [System Architecture Overview](./system/architecture-overview.md)
   - [Project Structure](./system/project-structure.md)

2. **코드 수정 전**:
   - 수정할 컴포넌트에 해당하는 SOP 먼저 확인
   - [Agent 수정 시](./SOPs/agent-modification-workflow.md)
   - [새로운 이벤트 타입 추가 시](./SOPs/new-event-type-implementation.md)

3. **배포 전**:
   - [Deployment Workflow](./SOPs/deployment-workflow.md) 필수 숙지

4. **유사 기능 구현 전**:
   - [Task 아카이브](./task/) 검색하여 기존 구현 참고

## 📁 문서 구조

```
.agent/
├── readme.md                    # 이 파일 (중앙 인덱스)
├── system/                      # 시스템 아키텍처 문서
│   ├── architecture-overview.md
│   ├── project-structure.md
│   └── event-pipeline.md
├── SOPs/                        # 표준 운영 절차
│   ├── agent-modification-workflow.md
│   ├── deployment-workflow.md
│   └── new-event-type-implementation.md
└── task/                        # 구현 계획 아카이브
    ├── README.md
    ├── completed/
    └── templates/
```

## 📚 문서 가이드

### System 문서 (system/)

프로젝트의 기본 구조와 아키텍처를 설명합니다. **모든 작업 전 한 번은 읽어야 합니다.**

| 문서 | 언제 읽어야 하는가 | 주요 내용 |
|-----|-----------------|---------|
| [architecture-overview.md](./system/architecture-overview.md) | 프로젝트 처음 접근 시<br>시스템 구조 이해 필요 시 | - 3계층 아키텍처 개요<br>- Agent, Control Plane, Dashboard 역할<br>- 데이터 흐름 |
| [project-structure.md](./system/project-structure.md) | 파일 위치를 모를 때<br>새로운 파일 추가 전 | - 디렉토리 구조<br>- 주요 파일 설명<br>- 빌드 산출물 |
| [event-pipeline.md](./system/event-pipeline.md) | 이벤트 관련 작업 시<br>이벤트 타입 추가/수정 시 | - 5가지 이벤트 타입<br>- 이벤트 흐름 상세<br>- 호환성 검증 |

### SOPs 문서 (SOPs/)

반복적인 작업에 대한 단계별 지침입니다. **작업 전 반드시 해당 SOP를 따라야 합니다.**

| 문서 | 언제 사용하는가 | 필수 준수 사항 |
|-----|---------------|--------------|
| [agent-modification-workflow.md](./SOPs/agent-modification-workflow.md) | Agent 코드 수정 시 | - agent-comprehensive-test-suite.sh 실행<br>- 100% 테스트 통과 필수<br>- Fallback 코드 무단 추가 금지 |
| [deployment-workflow.md](./SOPs/deployment-workflow.md) | 시스템 배포 시 | - Makefile 기반 배포 권장<br>- 이벤트 파이프라인 검증 필수<br>- Public DNS로 엔드포인트 확인 |
| [new-event-type-implementation.md](./SOPs/new-event-type-implementation.md) | 새로운 이벤트 타입 추가 시 | - 3개 레이어 모두 수정 필수<br>- 이벤트 스키마 설계<br>- 전체 파이프라인 테스트 |

### Task 문서 (task/)

과거 구현된 기능의 계획서와 결과를 저장합니다. **유사 기능 구현 전 참고해야 합니다.**

| 디렉토리 | 용도 | 사용 방법 |
|---------|------|----------|
| [task/README.md](./task/README.md) | Task 아카이브 가이드 | Task 디렉토리 사용법 참조 |
| task/completed/ | 완료된 구현 계획 | 유사 기능 검색 및 참고 |
| task/templates/ | PRD 템플릿 | 새로운 기능 계획 시 사용 |

## 🔍 상황별 문서 찾기

### 상황 1: "Agent 코드를 수정해야 해"

**단계**:
1. ✅ [agent-modification-workflow.md](./SOPs/agent-modification-workflow.md) 먼저 읽기
2. ✅ 코드 수정
3. ✅ `./scripts/agent-comprehensive-test-suite.sh` 실행
4. ✅ 100% 통과 확인
5. ✅ 배포

**주의 사항**:
- ❌ Fallback 코드 무단 추가 금지
- ❌ Mocking 코드 무단 추가 금지
- ✅ 테스트 실패 시 배포 금지

### 상황 2: "새로운 모니터링 기능을 추가해야 해"

**단계**:
1. ✅ [new-event-type-implementation.md](./SOPs/new-event-type-implementation.md) 읽기
2. ✅ [task/templates/feature-prd-template.md](./task/templates/feature-prd-template.md) 사용하여 계획 작성
3. ✅ 이벤트 타입 설계 (snake_case 사용)
4. ✅ Agent → Control Plane → Dashboard 순서로 구현
5. ✅ `./scripts/event-pipeline-verification-test.sh` 실행
6. ✅ 100% 통과 확인 후 배포

**필수 확인**:
- Agent에서 이벤트 생성
- Control Plane에서 이벤트 중계
- Dashboard에서 이벤트 표시

### 상황 3: "시스템을 배포해야 해"

**단계**:
1. ✅ [deployment-workflow.md](./SOPs/deployment-workflow.md) 읽기
2. ✅ `make test` 또는 `make full-test` 실행
3. ✅ `make build-and-deploy-all` 실행
4. ✅ Agent 로드 확인 (로그에서 "✅ ByteBuddy Agent Builder 설치 완료" 확인)
5. ✅ Public DNS로 엔드포인트 동작 확인
6. ✅ Dashboard에서 실시간 메트릭 확인

**주의 사항**:
- localhost portforward 대신 public DNS 사용
- 환경 변수 전달 확인

### 상황 4: "이벤트가 Dashboard에 표시되지 않아"

**디버깅 순서**:
1. ✅ [event-pipeline.md](./system/event-pipeline.md) "트러블슈팅" 섹션 참조
2. ✅ Agent 로그 확인
   ```bash
   kubectl logs <pod-name> | grep "KubeDB Monitor"
   ```
3. ✅ Control Plane 로그 확인
   ```bash
   kubectl logs deployment/kubedb-monitor-control-plane -n kubedb-monitor-test
   ```
4. ✅ Dashboard 브라우저 콘솔 확인 (WebSocket 연결 상태)

### 상황 5: "유사한 기능을 구현한 적이 있나?"

**검색 방법**:
1. ✅ [task/](./task/) 디렉토리 검색
2. ✅ [task/completed/](./task/completed/) 에서 유사 구현 찾기
3. ✅ 기존 구현의 "배운 점" 섹션 확인
4. ✅ 개선된 버전으로 재구현

## ⚠️ 필수 준수 사항 (CLAUDE.md와 일치)

이 규칙들은 프로젝트 루트의 [CLAUDE.md](../CLAUDE.md)에도 명시되어 있습니다.

### ❌ 금지 사항

1. **Fallback 코드 무단 추가 금지**
   - "안전모드", "safe mode", "fallback code" 등을 사용자에게 알리지 않고 함부로 추가 금지
   - 이전에 정상 작동하던 코드를 안전성을 이유로 임의 수정 금지

2. **Mocking 코드 무단 추가 금지**
   - 서버 측 simulation 코드 작업 시 물어보고 진행
   - 디버깅용 시뮬레이션, 모킹 코드는 기능 구현 후 반드시 삭제

3. **Agent JAR 경로 임의 변경 금지**
   - ✅ 올바른 경로: `/opt/kubedb-agent/kubedb-monitor-agent.jar`
   - ❌ 잘못된 경로: `/opt/shared-agent/`, `/app/`

### ✅ 필수 실행 사항

1. **Agent 수정 시 테스트 필수**
   ```bash
   ./scripts/agent-comprehensive-test-suite.sh
   ```
   성공률 100% 달성 시에만 배포 승인

2. **새로운 기능 구현 시 이벤트 파이프라인 검증 필수**
   ```bash
   ./scripts/event-pipeline-verification-test.sh
   ```
   성공률 100%가 아닐 경우 배포 금지

3. **TDD 적극 활용**
   - 가능한 테스트 케이스 먼저 생성
   - 테스트 스위트 작동 여부 검증

4. **이벤트 스키마 변경 시 호환성 확인**
   - Agent → Control Plane → Dashboard 서비스 레이어를 넘어가는 부분에서 이벤트 포맷/스키마 변경 시 다음 레이어 호환 확인

5. **Docker 이미지 업데이트 후 버전 확인**
   - 실행 버전이 최신 버전인지 항상 확인

## 🔄 문서 업데이트 규칙

### 언제 문서를 업데이트해야 하는가

1. **새로운 기능 구현 후**:
   - Task 계획서를 `task/completed/`로 이동
   - "구현 결과" 섹션 추가

2. **새로운 이벤트 타입 추가 후**:
   - [event-pipeline.md](./system/event-pipeline.md) 업데이트
   - [docs/agent-event-data-structures.md](../docs/agent-event-data-structures.md) 업데이트

3. **아키텍처 변경 후**:
   - [architecture-overview.md](./system/architecture-overview.md) 업데이트

4. **새로운 SOP 발견 시**:
   - `SOPs/` 디렉토리에 새로운 SOP 문서 추가
   - 이 readme.md에 링크 추가

### 문서 업데이트 워크플로우

```bash
# 1. 문서 수정
vi .agent/system/architecture-overview.md

# 2. Git 커밋
git add .agent/
git commit -m "docs: Update architecture documentation

- 변경 내용 요약

🤖 Generated with Claude Code
Co-Authored-By: Claude <noreply@anthropic.com>"

# 3. Push
git push origin <branch-name>
```

## 🔗 외부 문서 참조

프로젝트 루트 및 docs/ 디렉토리에도 중요한 문서들이 있습니다:

### 프로젝트 루트
- [README.md](../README.md): 프로젝트 개요 및 사용법
- [CLAUDE.md](../CLAUDE.md): AI 에이전트 메모리 파일 (이 문서와 동기화 필수)
- [Makefile](../Makefile): 빌드 및 배포 명령어

### docs/ 디렉토리
- [agent-jdbc-compatibility-guide.md](../docs/agent-jdbc-compatibility-guide.md): JDBC 호환성 가이드
- [agent-event-data-structures.md](../docs/agent-event-data-structures.md): 이벤트 데이터 구조
- [websocket-message-format.md](../docs/websocket-message-format.md): WebSocket 메시지 포맷
- [bytebuddy-agent-implementation-report.md](../docs/bytebuddy-agent-implementation-report.md): ByteBuddy Agent 구현 보고서

## 📊 문서 메트릭

- **System 문서**: 3개
- **SOPs 문서**: 3개
- **Task 템플릿**: 1개
- **완료된 Task**: 0개 (향후 누적)

## 🎓 학습 리소스

### 새로운 AI 에이전트를 위한 온보딩 순서

1. **프로젝트 이해** (30분):
   - [README.md](../README.md)
   - [architecture-overview.md](./system/architecture-overview.md)
   - [project-structure.md](./system/project-structure.md)

2. **이벤트 파이프라인 이해** (20분):
   - [event-pipeline.md](./system/event-pipeline.md)
   - [agent-event-data-structures.md](../docs/agent-event-data-structures.md)

3. **작업 워크플로우 학습** (30분):
   - [agent-modification-workflow.md](./SOPs/agent-modification-workflow.md)
   - [deployment-workflow.md](./SOPs/deployment-workflow.md)

4. **실습** (1시간):
   - 테스트 스크립트 실행
   - Dashboard 접속 및 메트릭 확인
   - 로그 모니터링

**총 예상 시간**: 약 2시간

## 📞 도움이 필요할 때

1. **문서에서 답을 찾을 수 없는 경우**:
   - 사용자에게 명확히 질문하기
   - 추측하지 말고 확인받기

2. **기존 규칙과 충돌하는 경우**:
   - 사용자에게 알리고 의견 구하기
   - CLAUDE.md의 규칙 우선 준수

3. **새로운 패턴을 발견한 경우**:
   - 해당 내용을 SOP로 문서화 제안
   - 이 readme.md에 추가 제안

## 🏷️ 버전 히스토리

- **v1.0** (2025-10-08): 초기 AI 에이전트 문서 시스템 구축
  - System 문서 3개 생성
  - SOPs 문서 3개 생성
  - Task 아카이브 구조 생성
  - 중앙 인덱스 파일 생성

---

**마지막 업데이트**: 2025-10-08
**유지보수 담당**: AI Agent + Human Developer
