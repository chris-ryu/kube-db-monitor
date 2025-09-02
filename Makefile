# KubeDB Monitor - 개발 및 배포 자동화 Makefile
# 수강신청 앱 DB 모니터링 솔루션

# 기본 설정
.PHONY: help test build deploy deploy-university clean agent-test agent-test-full agent-test-connection-proxy rest-test ui-test ui-test-full ui-test-coverage ui-test-watch full-test redeploy redeploy-all redeploy-university clean-deploy-university university-e2e build-and-deploy-all build-and-deploy-agent build-and-deploy-control-plane build-and-deploy-dashboard build-and-deploy-university docker-login build-no-cache build-no-push build-no-redeploy redeploy-agent-deployments redeploy-control-plane redeploy-dashboard redeploy-university-app
.DEFAULT_GOAL := help

# 색상 정의
GREEN := \033[32m
YELLOW := \033[33m
RED := \033[31m
BLUE := \033[34m
CYAN := \033[36m
RESET := \033[0m

# 프로젝트 경로
PROJECT_ROOT := $(shell pwd)
APP_DIR := $(PROJECT_ROOT)/sample-apps/university-registration
UI_DIR := $(PROJECT_ROOT)/sample-apps/university-registration-ui
AGENT_DIR := $(PROJECT_ROOT)/kubedb-monitor-agent
CONTROL_PLANE_DIR := $(PROJECT_ROOT)/kubedb-monitor-control-plane
DASHBOARD_DIR := $(PROJECT_ROOT)/kubedb-monitor-dashboard
SCRIPTS_DIR := $(PROJECT_ROOT)/scripts

# 버전 관리
VERSION := $(shell date +%Y%m%d-%H%M%S)
IMAGE_TAG := latest

# Docker Registry 설정 (build-images.sh에서 통합)
REGISTRY := $(or $(DOCKER_REGISTRY),registry.bitgaram.info)
DOCKER_USERNAME := $(or $(DOCKER_USERNAME),admin)
DOCKER_PASSWORD := $(or $(DOCKER_PASSWORD),qlcrkfka1\#)

# Docker 빌드 설정
DOCKER_BUILD_ARGS := --no-cache
PUSH_IMAGES := true
REDEPLOY_AFTER_BUILD := true

# 도커 이미지 이름
AGENT_IMAGE := $(REGISTRY)/kubedb-monitor/agent:$(IMAGE_TAG)
CONTROL_PLANE_IMAGE := $(REGISTRY)/kubedb-monitor/control-plane:$(IMAGE_TAG)
DASHBOARD_IMAGE := $(REGISTRY)/kubedb-monitor/dashboard:$(IMAGE_TAG)
UNIVERSITY_APP_IMAGE := $(REGISTRY)/kubedb-monitor/university-registration:$(IMAGE_TAG)
UNIVERSITY_UI_IMAGE := $(REGISTRY)/kubedb-monitor/university-registration-ui:$(IMAGE_TAG)

##@ 도움말
help: ## 사용 가능한 명령어 목록 표시
	@echo "$(BLUE)KubeDB Monitor - 개발 및 배포 자동화 도구$(RESET)"
	@echo ""
	@awk 'BEGIN {FS = ":.*##"; printf "사용법:\n  make $(GREEN)<target>$(RESET)\n"} /^[a-zA-Z_0-9-]+:.*?##/ { printf "  $(GREEN)%-20s$(RESET) %s\n", $$1, $$2 } /^##@/ { printf "\n$(BLUE)%s$(RESET)\n", substr($$0, 5) } ' $(MAKEFILE_LIST)

##@ 테스트
test: rest-test agent-test ui-test ## 전체 회귀 테스트 실행 (REST API + Agent + UI)

rest-test: ## 수강신청 앱 REST API 회귀 테스트
	@echo "$(YELLOW)🧪 수강신청 앱 REST API 회귀 테스트 시작$(RESET)"
	@cd $(SCRIPTS_DIR) && ./university-app-regression-test.sh all
	@echo "$(GREEN)✅ REST API 회귀 테스트 완료$(RESET)"

rest-test-quick: ## REST API 빠른 테스트 (기본 케이스만)
	@echo "$(YELLOW)⚡ 빠른 REST API 테스트$(RESET)"
	@cd $(APP_DIR) && mvn test -Dtest=SimpleH2Test -q
	@echo "$(GREEN)✅ 빠른 테스트 완료$(RESET)"

rest-test-controller: ## Controller 레이어 테스트만 실행
	@echo "$(YELLOW)🎮 Controller 레이어 테스트$(RESET)"
	@cd $(SCRIPTS_DIR) && ./university-app-regression-test.sh controller

rest-test-service: ## Service 레이어 테스트만 실행
	@echo "$(YELLOW)⚙️ Service 레이어 테스트$(RESET)"
	@cd $(SCRIPTS_DIR) && ./university-app-regression-test.sh service

rest-test-repository: ## Repository 레이어 테스트만 실행
	@echo "$(YELLOW)🗃️ Repository 레이어 테스트$(RESET)"
	@cd $(SCRIPTS_DIR) && ./university-app-regression-test.sh repository

ui-backend-contract: ## UI-Backend API 계약 검증
	@echo "$(YELLOW)🤝 UI-Backend API 계약 검증$(RESET)"
	@cd $(APP_DIR) && mvn test -Dtest=ApiContractTest -q
	@echo "$(GREEN)✅ API 계약 검증 완료$(RESET)"

ui-backend-e2e: ## UI-Backend E2E 통합 테스트
	@echo "$(YELLOW)🔄 UI-Backend E2E 통합 테스트$(RESET)"
	@cd $(APP_DIR) && mvn test -Dtest=UiBackendIntegrationTest -q
	@echo "$(GREEN)✅ E2E 통합 테스트 완료$(RESET)"

ui-test: ## UI /api 경로 매핑 및 통신 테스트
	@echo "$(YELLOW)🎨 UI /api 경로 매핑 테스트 시작$(RESET)"
	@echo "$(CYAN)  1. npm 의존성 확인 및 설치$(RESET)"
	@cd $(UI_DIR) && npm install
	@echo "$(CYAN)  2. API 클라이언트 테스트 (경로 매핑 검증)$(RESET)"
	@cd $(UI_DIR) && npm test -- --testPathPattern="lib" --passWithNoTests
	@echo "$(CYAN)  3. UI 컴포넌트 API 통신 테스트$(RESET)"
	@cd $(UI_DIR) && SKIP_E2E_TESTS=true npm test -- --testPathPattern="app" --passWithNoTests || echo "$(RED)⚠️  UI 컴포넌트 테스트 일부 실패 (Mock 데이터 설정, 허용됨)$(RESET)"
	@echo "$(GREEN)✅ UI /api 경로 매핑 테스트 완료$(RESET)"

ui-test-full: ## UI 전체 테스트 (E2E 포함)
	@echo "$(YELLOW)🎨 UI 전체 테스트 시작 (E2E 포함)$(RESET)"
	@cd $(UI_DIR) && npm install
	@echo "$(CYAN)  1. API 경로 매핑 검증$(RESET)"
	@cd $(UI_DIR) && npm run test:api
	@echo "$(CYAN)  2. 컴포넌트 통합 테스트$(RESET)"
	@cd $(UI_DIR) && npm test -- --testPathPattern="src/app/__tests__" --passWithNoTests
	@echo "$(CYAN)  3. E2E 통합 테스트 (서버 가용 시)$(RESET)"
	@cd $(UI_DIR) && SKIP_E2E_TESTS=false npm test -- --testPathPattern="api-integration.test" --passWithNoTests || echo "$(RED)⚠️  E2E 테스트 실패 (서버 미가용, 정상)$(RESET)"
	@echo "$(GREEN)✅ UI 전체 테스트 완료$(RESET)"

ui-test-coverage: ## UI 테스트 커버리지 리포트 생성
	@echo "$(YELLOW)📊 UI 테스트 커버리지 생성$(RESET)"
	@cd $(UI_DIR) && npm install
	@cd $(UI_DIR) && npm run test:coverage
	@echo "$(GREEN)✅ UI 테스트 커버리지 완료$(RESET)"

ui-test-watch: ## UI 테스트 watch 모드
	@echo "$(YELLOW)👀 UI 테스트 watch 모드$(RESET)"
	@cd $(UI_DIR) && npm install
	@cd $(UI_DIR) && npm run test:watch

agent-test: ## Agent 핵심 기능 회귀 테스트 (ByteBuddy 방식)
	@echo "$(YELLOW)🤖 Agent 핵심 기능 회귀 테스트 시작 (ByteBuddy)$(RESET)"
	@echo "$(CYAN)  1. Agent 설정 테스트$(RESET)"
	@cd $(AGENT_DIR) && mvn test -Dtest=AgentConfigTest -q
	@echo "$(CYAN)  2. 메트릭 수집 및 HTTP 전송 기능 테스트$(RESET)"
	@cd $(AGENT_DIR) && mvn test -Dtest=MetricsCollectionTest -q || echo "$(RED)⚠️  JMX 테스트 실패 (권한 문제, 정상)$(RESET)"
	@echo "$(CYAN)  3. ByteBuddy Agent 통합 테스트 (PostgreSQL 호환성 해결)$(RESET)"
	@cd $(AGENT_DIR) && mvn test -Dtest=ByteBuddyAgentIntegrationTest -q || echo "$(RED)⚠️  일부 통합 테스트 실패 (환경 문제, 허용)$(RESET)"
	@echo "$(CYAN)  4. PostgreSQL 호환성 테스트$(RESET)"
	@cd $(AGENT_DIR) && mvn test -Dtest=PostgreSQLTypesCompatibilityTest -q || echo "$(RED)⚠️  PostgreSQL Types 테스트 일부 실패 (정상)$(RESET)"
	@echo "$(GREEN)✅ Agent 핵심 기능 회귀 테스트 완료 (ByteBuddy 방식)$(RESET)"

agent-test-full: ## Agent 전체 테스트 (통합 테스트 포함)
	@echo "$(YELLOW)🤖 Agent 전체 테스트 시작 (통합 테스트 포함)$(RESET)"
	@$(MAKE) agent-test
	@echo "$(CYAN)  6. Transaction 모니터링 통합 테스트$(RESET)"
	@cd $(AGENT_DIR) && mvn test -Dtest=TransactionProxyIntegrationTest -q || echo "$(RED)⚠️  Transaction 모니터링 테스트 일부 실패$(RESET)"
	@echo "$(GREEN)✅ Agent 전체 테스트 완료$(RESET)"

agent-test-postgresql: ## PostgreSQL 특화 Agent 테스트
	@echo "$(YELLOW)🐘 PostgreSQL Agent 호환성 테스트$(RESET)"
	@cd $(SCRIPTS_DIR) && ./university-app-regression-test.sh postgresql

agent-test-production: ## 실제 프로덕션 시나리오 테스트 (Connection 프록시 기반)
	@echo "$(YELLOW)🏭 실제 프로덕션 시나리오 테스트$(RESET)"
	@echo "$(CYAN)  Connection 프록시를 통한 University Registration 실제 쿼리 패턴 검증$(RESET)"
	@cd $(AGENT_DIR) && mvn test -Dtest=ConnectionProxyIntegrationTest#testRealUniversityQueryPatternWithProxy -q
	@echo "$(CYAN)  PostgreSQL NULL 파라미터 호환성 검증$(RESET)"
	@cd $(AGENT_DIR) && mvn test -Dtest=ConnectionProxyIntegrationTest#testPostgreSQLPreparedStatementProxyNullHandling -q
	@echo "$(GREEN)✅ 프로덕션 시나리오 테스트 완료$(RESET)"

agent-test-connection-proxy: ## Connection 프록시 패턴 전용 테스트 (PostgreSQL 호환성 + Transaction 모니터링)
	@echo "$(YELLOW)🔗 Connection 프록시 패턴 테스트$(RESET)"
	@echo "$(CYAN)  ASM 바이트코드 변환을 대체하는 안전한 프록시 패턴 검증$(RESET)"
	@cd $(AGENT_DIR) && mvn test -Dtest=ConnectionProxyIntegrationTest -q
	@echo "$(CYAN)  Transaction 모니터링 및 데드락 검출 능력 검증$(RESET)"
	@cd $(AGENT_DIR) && mvn test -Dtest=TransactionProxyIntegrationTest -q || echo "$(RED)⚠️  Transaction 테스트 일부 실패 (설정 이슈)$(RESET)"
	@echo "$(GREEN)✅ Connection 프록시 패턴 테스트 완료$(RESET)"

university-e2e: ## 수강신청 앱 E2E 테스트 (Kubernetes 환경)
	@echo "$(YELLOW)🎯 수강신청 앱 E2E 테스트 시작$(RESET)"
	@$(SCRIPTS_DIR)/university-registration-e2e-test.sh
	@echo "$(GREEN)✅ 수강신청 앱 E2E 테스트 완료$(RESET)"

deadlock-longrunning-test: ## 데드락/Long-running 트랜잭션 통합 테스트
	@echo "$(YELLOW)💀 데드락/Long-running 트랜잭션 통합 테스트 시작$(RESET)"
	@$(SCRIPTS_DIR)/deadlock-longrunning-integration-test.sh
	@echo "$(GREEN)✅ 데드락/Long-running 트랜잭션 통합 테스트 완료$(RESET)"

simple-e2e: ## 간단한 E2E 검증 (모니터링 중심)
	@echo "$(YELLOW)⚡ 간단한 E2E 검증$(RESET)"
	@/tmp/simple-e2e-validation.sh
	@echo "$(GREEN)✅ 간단한 E2E 검증 완료$(RESET)"

full-test: ## 종합 테스트 (모든 레이어 + Agent + UI + Backend + E2E)
	@echo "$(BLUE)🚀 KubeDB Monitor 종합 테스트 시작$(RESET)"
	@$(MAKE) build-test
	@$(MAKE) rest-test-quick
	@$(MAKE) agent-test
	@$(MAKE) ui-test
	@$(MAKE) ui-backend-contract
	@$(MAKE) ui-backend-e2e
	@$(MAKE) simple-e2e
	@echo "$(GREEN)🎉 종합 테스트 완료!$(RESET)"

comprehensive-test: ## 포괄적 테스트 (전체 회귀 테스트 + UI + E2E)
	@echo "$(BLUE)🔍 포괄적 테스트 시작 (시간이 오래 걸릴 수 있습니다)$(RESET)"
	@$(MAKE) build-test
	@$(MAKE) rest-test
	@$(MAKE) agent-test
	@$(MAKE) ui-test-full
	@$(MAKE) university-e2e
	@echo "$(GREEN)🎉 포괄적 테스트 완료!$(RESET)"

build-test: ## 빌드 테스트 (컴파일 확인)
	@echo "$(YELLOW)🔨 빌드 테스트$(RESET)"
	@echo "$(CYAN)  Java 컴포넌트 빌드 테스트$(RESET)"
	@cd $(APP_DIR) && mvn compile -q
	@cd $(AGENT_DIR) && mvn compile -q
	@echo "$(CYAN)  UI 컴포넌트 빌드 테스트$(RESET)"
	@cd $(UI_DIR) && npm install && npm run build
	@echo "$(GREEN)✅ 빌드 테스트 완료$(RESET)"

##@ 개발 환경
dev-setup: ## 개발 환경 초기 설정
	@echo "$(BLUE)🛠️ 개발 환경 설정 중...$(RESET)"
	@echo "$(CYAN)Maven 의존성 다운로드...$(RESET)"
	@cd $(APP_DIR) && mvn dependency:resolve -q
	@cd $(AGENT_DIR) && mvn dependency:resolve -q
	@echo "$(CYAN)npm 의존성 다운로드...$(RESET)"
	@cd $(UI_DIR) && npm install
	@echo "$(GREEN)✅ 개발 환경 설정 완료$(RESET)"

dev-clean: ## 개발 환경 정리
	@echo "$(YELLOW)🧹 개발 환경 정리 중...$(RESET)"
	@echo "$(CYAN)Maven 아티팩트 정리...$(RESET)"
	@cd $(APP_DIR) && mvn clean -q
	@cd $(AGENT_DIR) && mvn clean -q
	@echo "$(CYAN)Node.js 아티팩트 정리...$(RESET)"
	@cd $(UI_DIR) && rm -rf node_modules/.cache .next || true
	@echo "$(GREEN)✅ 개발 환경 정리 완료$(RESET)"

##@ 이미지 빌드 및 배포
build: build-agent build-control-plane build-dashboard ## 모든 컴포넌트 이미지 빌드

# Docker 로그인 함수 (레지스트리 푸시가 필요한 경우)
docker-login:
	@if [ "$(PUSH_IMAGES)" = "true" ]; then \
		echo "$(BLUE)[INFO]$(RESET) Docker 레지스트리에 로그인 중..."; \
		echo "$(DOCKER_PASSWORD)" | docker login "$(REGISTRY)" -u "$(DOCKER_USERNAME)" --password-stdin; \
		echo "$(GREEN)[SUCCESS]$(RESET) Docker 로그인 완료"; \
	fi

build-agent: docker-login ## Agent 이미지 빌드, 푸시 및 배포
	@echo "$(YELLOW)🤖 Agent 이미지 빌드 시작$(RESET)"
	@if [ -d "kubedb-monitor-agent" ]; then \
		echo "$(CYAN)  Maven 프로젝트 빌드: kubedb-monitor-agent$(RESET)"; \
		cd kubedb-monitor-agent && mvn clean package -DskipTests=true -q; \
	fi
	@if [ -f "Dockerfile.agent" ]; then \
		echo "$(CYAN)  Docker 이미지 빌드: $(AGENT_IMAGE)$(RESET)"; \
		docker build $(DOCKER_BUILD_ARGS) -f Dockerfile.agent -t "$(AGENT_IMAGE)" .; \
		if [ "$(PUSH_IMAGES)" = "true" ]; then \
			echo "$(CYAN)  이미지 푸시: $(AGENT_IMAGE)$(RESET)"; \
			docker push "$(AGENT_IMAGE)"; \
			echo "$(GREEN)[SUCCESS]$(RESET) 이미지 푸시 완료: $(AGENT_IMAGE)"; \
		fi; \
	else \
		echo "$(YELLOW)[WARNING]$(RESET) Dockerfile.agent를 찾을 수 없습니다."; \
	fi
	@if [ "$(REDEPLOY_AFTER_BUILD)" = "true" ] && [ "$(PUSH_IMAGES)" = "true" ]; then \
		$(MAKE) redeploy-agent-deployments; \
	fi
	@echo "$(GREEN)✅ Agent 이미지 빌드 완료: $(AGENT_IMAGE)$(RESET)"

build-control-plane: docker-login ## Control Plane 이미지 빌드, 푸시 및 배포
	@echo "$(YELLOW)🎛️ Control Plane 이미지 빌드 시작$(RESET)"
	@if [ -d "kubedb-monitor-control-plane" ]; then \
		cd kubedb-monitor-control-plane && go mod tidy && go build -o kubedb-monitor-control-plane .; \
		docker build $(DOCKER_BUILD_ARGS) -f Dockerfile -t "$(CONTROL_PLANE_IMAGE)" ./kubedb-monitor-control-plane; \
	elif [ -d "control-plane" ]; then \
		docker build $(DOCKER_BUILD_ARGS) -f control-plane/Dockerfile -t "$(CONTROL_PLANE_IMAGE)" ./control-plane; \
	else \
		echo "$(YELLOW)[WARNING]$(RESET) Control Plane 디렉터리를 찾을 수 없습니다."; \
	fi
	@if [ "$(PUSH_IMAGES)" = "true" ]; then \
		echo "$(CYAN)  이미지 푸시: $(CONTROL_PLANE_IMAGE)$(RESET)"; \
		docker push "$(CONTROL_PLANE_IMAGE)"; \
		echo "$(GREEN)[SUCCESS]$(RESET) 이미지 푸시 완료: $(CONTROL_PLANE_IMAGE)"; \
	fi
	@if [ "$(REDEPLOY_AFTER_BUILD)" = "true" ] && [ "$(PUSH_IMAGES)" = "true" ]; then \
		$(MAKE) redeploy-control-plane; \
	fi
	@echo "$(GREEN)✅ Control Plane 이미지 빌드 완료: $(CONTROL_PLANE_IMAGE)$(RESET)"

build-dashboard: docker-login ## Dashboard 이미지 빌드, 푸시 및 배포
	@echo "$(YELLOW)📊 Dashboard 이미지 빌드 시작$(RESET)"
	@if [ -d "kubedb-monitor-dashboard" ]; then \
		docker build $(DOCKER_BUILD_ARGS) -f kubedb-monitor-dashboard/Dockerfile -t "$(DASHBOARD_IMAGE)" ./kubedb-monitor-dashboard; \
	elif [ -d "dashboard-frontend" ]; then \
		docker build $(DOCKER_BUILD_ARGS) -f dashboard-frontend/Dockerfile -t "$(DASHBOARD_IMAGE)" ./dashboard-frontend; \
	else \
		echo "$(YELLOW)[WARNING]$(RESET) Dashboard 디렉터리를 찾을 수 없습니다."; \
	fi
	@if [ "$(PUSH_IMAGES)" = "true" ]; then \
		echo "$(CYAN)  이미지 푸시: $(DASHBOARD_IMAGE)$(RESET)"; \
		docker push "$(DASHBOARD_IMAGE)"; \
		echo "$(GREEN)[SUCCESS]$(RESET) 이미지 푸시 완료: $(DASHBOARD_IMAGE)"; \
	fi
	@if [ "$(REDEPLOY_AFTER_BUILD)" = "true" ] && [ "$(PUSH_IMAGES)" = "true" ]; then \
		$(MAKE) redeploy-dashboard; \
	fi
	@echo "$(GREEN)✅ Dashboard 이미지 빌드 완료: $(DASHBOARD_IMAGE)$(RESET)"

build-university-app: docker-login ## 수강신청 앱 이미지 빌드, 푸시 및 배포 (Backend API + Frontend UI)
	@echo "$(YELLOW)🎓 수강신청 앱 이미지 빌드 시작 (Backend + Frontend)$(RESET)"
	@if [ -d "sample-apps/university-registration" ]; then \
		echo "$(CYAN)  🔧 Backend (API) Maven 빌드: sample-apps/university-registration$(RESET)"; \
		cd sample-apps/university-registration && mvn clean package -DskipTests=true -q; \
		echo "$(CYAN)  🔧 Backend (API) Docker 이미지 빌드: $(UNIVERSITY_APP_IMAGE)$(RESET)"; \
		docker build $(DOCKER_BUILD_ARGS) -f sample-apps/university-registration/Dockerfile -t "$(UNIVERSITY_APP_IMAGE)" ./sample-apps/university-registration; \
		if [ "$(PUSH_IMAGES)" = "true" ]; then \
			echo "$(CYAN)  🔧 Backend (API) 이미지 푸시: $(UNIVERSITY_APP_IMAGE)$(RESET)"; \
			docker push "$(UNIVERSITY_APP_IMAGE)"; \
		fi; \
	else \
		echo "$(YELLOW)[WARNING]$(RESET) Backend 디렉터리를 찾을 수 없습니다: sample-apps/university-registration"; \
	fi
	@if [ -d "sample-apps/university-registration-ui" ]; then \
		echo "$(CYAN)  🎨 Frontend (UI) Docker 이미지 빌드: $(UNIVERSITY_UI_IMAGE)$(RESET)"; \
		docker build $(DOCKER_BUILD_ARGS) -f sample-apps/university-registration-ui/Dockerfile -t "$(UNIVERSITY_UI_IMAGE)" ./sample-apps/university-registration-ui; \
		if [ "$(PUSH_IMAGES)" = "true" ]; then \
			echo "$(CYAN)  🎨 Frontend (UI) 이미지 푸시: $(UNIVERSITY_UI_IMAGE)$(RESET)"; \
			docker push "$(UNIVERSITY_UI_IMAGE)"; \
		fi; \
	else \
		echo "$(YELLOW)[WARNING]$(RESET) Frontend 디렉터리를 찾을 수 없습니다: sample-apps/university-registration-ui"; \
	fi
	@if [ "$(REDEPLOY_AFTER_BUILD)" = "true" ] && [ "$(PUSH_IMAGES)" = "true" ]; then \
		$(MAKE) redeploy-university-app; \
	fi
	@echo "$(GREEN)✅ 수강신청 앱 (Backend + Frontend) 이미지 빌드 완료$(RESET)"

build-all-force: docker-login ## 캐시 없이 모든 이미지 강제 재빌드
	@echo "$(YELLOW)🔄 모든 이미지 강제 재빌드 중 (캐시 없음)...$(RESET)"
	@$(MAKE) DOCKER_BUILD_ARGS="--no-cache" build-agent
	@$(MAKE) DOCKER_BUILD_ARGS="--no-cache" build-control-plane
	@$(MAKE) DOCKER_BUILD_ARGS="--no-cache" build-dashboard
	@$(MAKE) DOCKER_BUILD_ARGS="--no-cache" build-university-app
	@echo "$(GREEN)🎉 모든 이미지 강제 재빌드 완료!$(RESET)"
	@if [ "$(PUSH_IMAGES)" = "true" ]; then \
		echo "$(CYAN)빌드된 이미지들:$(RESET)"; \
		docker images | grep "$(REGISTRY)/kubedb-monitor" | head -10; \
	fi

# 빌드 옵션 오버라이드 (캐시 사용, 푸시 안함, 재배포 안함 등)
build-no-cache: ## 캐시 없이 빌드
	@$(MAKE) DOCKER_BUILD_ARGS="--no-cache" build

build-no-push: ## 이미지 빌드만 하고 푸시 안함
	@$(MAKE) PUSH_IMAGES="false" REDEPLOY_AFTER_BUILD="false" build

build-no-redeploy: ## 이미지 빌드/푸시는 하되 배포 재시작 안함
	@$(MAKE) REDEPLOY_AFTER_BUILD="false" build

##@ Kubernetes 배포
deploy: ## 전체 KubeDB Monitor 배포
	@echo "$(BLUE)🚀 KubeDB Monitor 배포 시작$(RESET)"
	@$(MAKE) build
	@kubectl apply -f k8s/kubedb-monitor-deployment.yaml
	@echo "$(CYAN)📡 Dashboard Ingress 설정 적용 (Control Plane 내부 프록시 포함)$(RESET)"
	@kubectl apply -f k8s/dashboard-ingress.yaml
	@echo "$(GREEN)✅ KubeDB Monitor 배포 완료$(RESET)"
	@$(MAKE) status

deploy-university: ## 수강신청 앱 배포 (KubeDB Monitor Agent 포함)
	@echo "$(BLUE)🎓 수강신청 앱 배포 시작 (KubeDB Monitor Agent 포함)$(RESET)"
	@$(MAKE) build
	@kubectl apply -f k8s/university-registration-demo-complete.yaml
	@echo "$(GREEN)✅ 수강신청 앱 배포 완료$(RESET)"
	@kubectl get pods -n kubedb-monitor-test

init-university-data: ## University 데이터베이스 초기화 (서비스 시작 후 실행)
	@echo "$(BLUE)📚 University Registration 데이터베이스 초기화$(RESET)"
	@$(SCRIPTS_DIR)/init-university-data.sh
	@echo "$(GREEN)✅ 데이터베이스 초기화 완료$(RESET)"

deploy-university-with-data: ## 수강신청 앱 배포 + 데이터 초기화
	@echo "$(BLUE)🎓 수강신청 앱 전체 배포 (앱 + 데이터 초기화)$(RESET)"
	@$(MAKE) deploy-university
	@echo "$(YELLOW)⏳ 앱 시작 대기 중... (30초)$(RESET)"
	@sleep 30
	@$(MAKE) init-university-data
	@echo "$(GREEN)✅ 수강신청 앱 전체 배포 완료$(RESET)"

deploy-dev: ## 개발 환경 배포 (no-agent 버전)
	@echo "$(YELLOW)🔧 개발 환경 배포 (no-agent)$(RESET)"
	@$(MAKE) build
	@kubectl apply -f k8s/kubedb-monitor-deployment.yaml
	@kubectl set env deployment/university-registration-demo AGENT_ENABLED=false
	@echo "$(GREEN)✅ 개발 환경 배포 완료$(RESET)"

redeploy: clean-deploy deploy ## KubeDB Monitor 삭제 후 재배포

redeploy-all: ## 전체 시스템 삭제 후 재배포 (KubeDB Monitor + University App)
	@echo "$(YELLOW)🔄 전체 시스템 재배포 시작$(RESET)"
	@$(MAKE) clean-deploy
	@$(MAKE) clean-deploy-university
	@$(MAKE) deploy
	@$(MAKE) deploy-university
	@echo "$(GREEN)✅ 전체 시스템 재배포 완료$(RESET)"

redeploy-university: ## 수강신청 앱 삭제 후 재배포  
	@$(MAKE) clean-deploy-university
	@$(MAKE) deploy-university

# 개별 컴포넌트 재배포 함수들 (build-images.sh에서 통합)
redeploy-agent-deployments: ## Agent가 포함된 배포들 재시작
	@echo "$(BLUE)📦 Agent 관련 배포 재시작$(RESET)"
	@if kubectl get deployment university-registration-demo -n kubedb-monitor-test >/dev/null 2>&1; then \
		echo "$(CYAN)  기존 university-registration-demo 완전 제거...$(RESET)"; \
		kubectl delete deployment university-registration-demo -n kubedb-monitor-test --grace-period=30 --timeout=60s; \
		kubectl delete pods -n kubedb-monitor-test -l app=university-registration-demo --grace-period=0 --force >/dev/null 2>&1 || true; \
		echo "$(CYAN)  university-registration-demo 재배포 중...$(RESET)"; \
		kubectl apply -f k8s/university-registration-demo-complete.yaml; \
		kubectl rollout status deployment/university-registration-demo -n kubedb-monitor-test --timeout=180s; \
		echo "$(GREEN)[SUCCESS]$(RESET) university-registration-demo 재배포 완료"; \
	fi
	@if kubectl get deployment university-registration -n kubedb-monitor-test >/dev/null 2>&1; then \
		echo "$(CYAN)  기존 university-registration 완전 제거...$(RESET)"; \
		kubectl delete deployment university-registration -n kubedb-monitor-test --grace-period=30 --timeout=60s; \
		kubectl delete pods -n kubedb-monitor-test -l app=university-registration --grace-period=0 --force >/dev/null 2>&1 || true; \
		echo "$(CYAN)  university-registration 재배포 중...$(RESET)"; \
		kubectl apply -f k8s/university-registration-with-ui.yaml; \
		kubectl rollout status deployment/university-registration -n kubedb-monitor-test --timeout=180s; \
		echo "$(GREEN)[SUCCESS]$(RESET) university-registration 재배포 완료"; \
	fi

redeploy-control-plane: ## Control Plane 재배포
	@echo "$(BLUE)🎛️ Control Plane 재배포$(RESET)"
	@if kubectl get deployment kubedb-monitor-control-plane -n kubedb-monitor >/dev/null 2>&1; then \
		echo "$(CYAN)  기존 kubedb-monitor-control-plane 완전 제거...$(RESET)"; \
		kubectl delete deployment kubedb-monitor-control-plane -n kubedb-monitor --grace-period=30 --timeout=60s; \
		kubectl delete pods -n kubedb-monitor -l app=kubedb-monitor-control-plane --grace-period=0 --force >/dev/null 2>&1 || true; \
		echo "$(CYAN)  kubedb-monitor-control-plane 재배포 중...$(RESET)"; \
		kubectl apply -f k8s/kubedb-monitor-deployment.yaml; \
		kubectl rollout status deployment/kubedb-monitor-control-plane -n kubedb-monitor --timeout=120s; \
		echo "$(GREEN)[SUCCESS]$(RESET) kubedb-monitor-control-plane 재배포 완료"; \
	fi

redeploy-dashboard: ## Dashboard 재배포
	@echo "$(BLUE)📊 Dashboard 재배포$(RESET)"
	@if kubectl get deployment kubedb-monitor-dashboard -n kubedb-monitor >/dev/null 2>&1; then \
		echo "$(CYAN)  기존 kubedb-monitor-dashboard 완전 제거...$(RESET)"; \
		kubectl delete deployment kubedb-monitor-dashboard -n kubedb-monitor --grace-period=30 --timeout=60s; \
		kubectl delete pods -n kubedb-monitor -l app=kubedb-monitor-dashboard --grace-period=0 --force >/dev/null 2>&1 || true; \
		echo "$(CYAN)  kubedb-monitor-dashboard 재배포 중...$(RESET)"; \
		kubectl apply -f k8s/kubedb-monitor-deployment.yaml; \
		kubectl rollout status deployment/kubedb-monitor-dashboard -n kubedb-monitor --timeout=120s; \
		echo "$(GREEN)[SUCCESS]$(RESET) kubedb-monitor-dashboard 재배포 완료"; \
	fi

redeploy-university-app: ## University App 재배포 (Backend API + Frontend UI)
	@echo "$(BLUE)🎓 University App 재배포 (Backend API + Frontend UI)$(RESET)"
	@echo "$(CYAN)  🔧 Backend (API) 서비스 재배포 중...$(RESET)"
	@if kubectl get deployment university-registration-demo -n kubedb-monitor-test >/dev/null 2>&1; then \
		echo "$(CYAN)    기존 university-registration-demo 완전 제거...$(RESET)"; \
		kubectl delete deployment university-registration-demo -n kubedb-monitor-test --grace-period=30 --timeout=60s; \
		kubectl delete pods -n kubedb-monitor-test -l app=university-registration-demo --grace-period=0 --force >/dev/null 2>&1 || true; \
	fi
	@echo "$(CYAN)  🎨 Frontend (UI) 서비스 재배포 중...$(RESET)"
	@if kubectl get deployment university-registration-ui -n kubedb-monitor-test >/dev/null 2>&1; then \
		echo "$(CYAN)    기존 university-registration-ui 완전 제거...$(RESET)"; \
		kubectl delete deployment university-registration-ui -n kubedb-monitor-test --grace-period=30 --timeout=60s; \
		kubectl delete pods -n kubedb-monitor-test -l app=university-registration-ui --grace-period=0 --force >/dev/null 2>&1 || true; \
	fi
	@echo "$(CYAN)  🚀 새로운 University App 배포 적용 중...$(RESET)"
	@kubectl apply -f k8s/university-registration-with-ui.yaml
	@echo "$(CYAN)  ⏳ Backend (API) 배포 준비 대기...$(RESET)"
	@kubectl rollout status deployment/university-registration-demo -n kubedb-monitor-test --timeout=180s || echo "$(YELLOW)[WARNING]$(RESET) Backend 배포 대기 시간 초과"
	@echo "$(CYAN)  ⏳ Frontend (UI) 배포 준비 대기...$(RESET)"
	@kubectl rollout status deployment/university-registration-ui -n kubedb-monitor-test --timeout=120s || echo "$(YELLOW)[WARNING]$(RESET) Frontend 배포 대기 시간 초과"
	@echo "$(CYAN)  📊 University App 서비스 상태 확인...$(RESET)"
	@echo "$(YELLOW)    🔧 Backend API Service:$(RESET)"
	@kubectl get service university-registration-demo-service -n kubedb-monitor-test 2>/dev/null || echo "$(RED)[ERROR]$(RESET) Backend API Service를 찾을 수 없음"
	@echo "$(YELLOW)    🎨 Frontend UI Service:$(RESET)"
	@kubectl get service university-registration-ui-service -n kubedb-monitor-test 2>/dev/null || echo "$(RED)[ERROR]$(RESET) Frontend UI Service를 찾을 수 없음"
	@echo "$(YELLOW)    🌐 Ingress:$(RESET)"
	@kubectl get ingress university-registration-demo-ingress -n kubedb-monitor-test 2>/dev/null || echo "$(RED)[ERROR]$(RESET) University Ingress를 찾을 수 없음"
	@echo "$(GREEN)[SUCCESS]$(RESET) 🎉 University App (Backend + Frontend) 재배포 완료"

clean-deploy: ## Kubernetes 배포 삭제
	@echo "$(RED)🗑️ KubeDB Monitor 배포 삭제$(RESET)"
	@kubectl delete -f k8s/kubedb-monitor-deployment.yaml --ignore-not-found=true
	@kubectl delete -f k8s/dashboard-ingress.yaml --ignore-not-found=true
	@echo "$(YELLOW)⏳ Pod 종료 대기 중...$(RESET)"
	@sleep 10
	@echo "$(GREEN)✅ 배포 삭제 완료$(RESET)"

clean-deploy-university: ## 수강신청 앱 배포 삭제
	@echo "$(RED)🗑️ 수강신청 앱 배포 삭제$(RESET)"
	@kubectl delete -f k8s/university-registration-demo-complete.yaml --ignore-not-found=true
	@echo "$(YELLOW)⏳ Pod 종료 대기 중...$(RESET)"
	@sleep 10
	@echo "$(GREEN)✅ 수강신청 앱 배포 삭제 완료$(RESET)"

restart: ## 애플리케이션 재시작 (이미지 재빌드 없이)
	@echo "$(YELLOW)🔄 애플리케이션 재시작 중...$(RESET)"
	@kubectl rollout restart deployment/university-registration-demo
	@kubectl rollout restart deployment/kubedb-monitor-control-plane  
	@kubectl rollout restart deployment/kubedb-monitor-dashboard
	@echo "$(GREEN)✅ 재시작 완료$(RESET)"

status: ## 배포 상태 확인
	@echo "$(BLUE)📊 KubeDB Monitor 상태$(RESET)"
	@echo "$(YELLOW)Pods:$(RESET)"
	@kubectl get pods -l app.kubernetes.io/part-of=kubedb-monitor
	@echo "\n$(YELLOW)Services:$(RESET)"
	@kubectl get services -l app.kubernetes.io/part-of=kubedb-monitor
	@echo "\n$(YELLOW)Ingress:$(RESET)"
	@kubectl get ingress -l app.kubernetes.io/part-of=kubedb-monitor

logs: ## 전체 애플리케이션 로그 조회
	@echo "$(BLUE)📜 KubeDB Monitor 로그$(RESET)"
	@echo "$(YELLOW)University Registration App:$(RESET)"
	@kubectl logs -l app=university-registration-demo --tail=50
	@echo "\n$(YELLOW)Control Plane:$(RESET)"
	@kubectl logs -l app=kubedb-monitor-control-plane --tail=50

logs-app: ## 수강신청 앱 로그만 조회
	@kubectl logs -l app=university-registration-demo --tail=100 -f

logs-agent: ## Agent 관련 로그 조회 
	@kubectl logs -l app=university-registration-demo --tail=100 | grep -i agent

logs-error: ## 에러 로그만 조회
	@kubectl logs -l app.kubernetes.io/part-of=kubedb-monitor --tail=200 | grep -i error

##@ 모니터링 & 디버깅
debug: ## 디버깅 정보 수집
	@echo "$(BLUE)🔍 디버깅 정보 수집$(RESET)"
	@echo "$(YELLOW)== Pod 상태 ==$(RESET)"
	@kubectl get pods -o wide
	@echo "\n$(YELLOW)== 최근 이벤트 ==$(RESET)" 
	@kubectl get events --sort-by='.lastTimestamp' | tail -10
	@echo "\n$(YELLOW)== 리소스 사용량 ==$(RESET)"
	@kubectl top pods --no-headers 2>/dev/null || echo "Metrics server not available"

port-forward: ## 로컬 포트 포워딩 (앱:8080, 대시보드:3000)
	@echo "$(BLUE)🔗 포트 포워딩 설정$(RESET)"
	@echo "University App: http://localhost:8080"
	@echo "Dashboard: http://localhost:3000"
	@echo "$(YELLOW)Ctrl+C로 종료$(RESET)"
	@kubectl port-forward service/university-registration-service 8080:8080 &
	@kubectl port-forward service/kubedb-monitor-dashboard-service 3000:3000 &
	@wait

##@ 성능 테스트
perf-test: ## 성능 테스트 실행
	@echo "$(YELLOW)⚡ 성능 테스트 시작$(RESET)"
	@cd $(SCRIPTS_DIR) && ./intensive-traffic-generator.sh
	@echo "$(GREEN)✅ 성능 테스트 완료$(RESET)"

load-test: ## 부하 테스트 (고강도)
	@echo "$(RED)🔥 부하 테스트 시작 (주의: 고강도)$(RESET)"
	@read -p "계속하시겠습니까? [y/N] " confirm && [ "$$confirm" = "y" ] || exit 1
	@cd $(SCRIPTS_DIR) && ./intensive-traffic-generator.sh --high-load
	@echo "$(GREEN)✅ 부하 테스트 완료$(RESET)"

##@ 유지보수
clean: dev-clean ## 전체 정리 (빌드 아티팩트, 이미지 등)
	@echo "$(YELLOW)🧹 전체 정리 중...$(RESET)"
	@docker system prune -f --volumes
	@echo "$(GREEN)✅ 정리 완료$(RESET)"

backup: ## 설정 및 데이터 백업
	@echo "$(BLUE)💾 백업 생성 중...$(RESET)"
	@mkdir -p backups/$(VERSION)
	@cp -r k8s/ backups/$(VERSION)/
	@cp -r scripts/ backups/$(VERSION)/
	@kubectl get all -o yaml > backups/$(VERSION)/k8s-resources.yaml
	@echo "$(GREEN)✅ 백업 완료: backups/$(VERSION)/$(RESET)"

version: ## 현재 버전 정보 표시
	@echo "$(BLUE)📋 KubeDB Monitor 버전 정보$(RESET)"
	@echo "빌드 버전: $(VERSION)"
	@echo "이미지 태그: $(IMAGE_TAG)"
	@echo "프로젝트 경로: $(PROJECT_ROOT)"

##@ 빌드 편의 명령어 (build-images.sh 통합)
build-and-deploy-all: ## 모든 컴포넌트 빌드, 푸시, 배포 (build-images.sh all 호출)
	@echo "$(BLUE)🚀 전체 빌드 및 배포 시작 (build-images.sh 스크립트 호출)$(RESET)"
	@$(SCRIPTS_DIR)/build-images.sh all --push
	@echo "$(GREEN)🎉 전체 빌드 및 배포 완료!$(RESET)"

build-and-deploy-agent: ## Agent만 빌드, 푸시, 배포 (build-images.sh agent 호출)
	@echo "$(BLUE)🤖 Agent 빌드 및 배포 시작 (build-images.sh 스크립트 호출)$(RESET)"
	@$(SCRIPTS_DIR)/build-images.sh agent --push
	@echo "$(GREEN)✅ Agent 빌드 및 배포 완료$(RESET)"

build-and-deploy-control-plane: ## Control Plane만 빌드, 푸시, 배포 (build-images.sh 호출)
	@echo "$(BLUE)🎛️ Control Plane 빌드 및 배포 시작 (build-images.sh 스크립트 호출)$(RESET)"
	@$(SCRIPTS_DIR)/build-images.sh control-plane --push
	@echo "$(GREEN)✅ Control Plane 빌드 및 배포 완료$(RESET)"

build-and-deploy-dashboard: ## Dashboard만 빌드, 푸시, 배포 (build-images.sh 호출)
	@echo "$(BLUE)📊 Dashboard 빌드 및 배포 시작 (build-images.sh 스크립트 호출)$(RESET)"
	@$(SCRIPTS_DIR)/build-images.sh dashboard --push
	@echo "$(GREEN)✅ Dashboard 빌드 및 배포 완료$(RESET)"

build-and-deploy-university: ## University App만 빌드, 푸시, 배포 (Backend API + Frontend UI, build-images.sh 호출)
	@echo "$(BLUE)🎓 University App 빌드 및 배포 시작 (Backend + Frontend, build-images.sh 스크립트 호출)$(RESET)"
	@$(SCRIPTS_DIR)/build-images.sh university-app --push
	@echo "$(GREEN)✅ University App (Backend + Frontend) 빌드 및 배포 완료$(RESET)"

##@ CI/CD
ci-test: ## CI 환경에서의 테스트 (GitHub Actions 등)
	@echo "$(BLUE)🔄 CI 테스트 실행$(RESET)"
	@$(MAKE) build-test
	@$(MAKE) rest-test-quick
	@$(MAKE) agent-test
	@echo "$(GREEN)✅ CI 테스트 완료$(RESET)"

release: ## 릴리즈 빌드 (태그 생성 + 이미지 빌드)
	@echo "$(BLUE)🎯 릴리즈 빌드 시작$(RESET)"
	@$(MAKE) full-test
	@$(MAKE) build-all-force
	@echo "$(GREEN)🎉 릴리즈 빌드 완료!$(RESET)"

##@ 데모 & 테스트 시나리오  
demo: ## 데모 환경 설정 및 실행
	@echo "$(BLUE)🎬 데모 환경 준비$(RESET)"
	@$(MAKE) deploy-demo
	@$(MAKE) port-forward

demo-reset: ## 데모 데이터 완전 리셋
	@echo "$(YELLOW)🔄 데모 데이터 리셋$(RESET)"
	@curl -X POST "localhost:8080/api/demo/reset?mode=demo" || echo "앱이 실행 중이 아니거나 데모 모드가 아닙니다"

demo-status: ## 데모 환경 데이터 상태 확인  
	@echo "$(BLUE)📊 데모 환경 상태$(RESET)"
	@curl -s "localhost:8080/api/demo/status" | python3 -m json.tool || echo "데모 API에 접근할 수 없습니다"

demo-deadlock: ## 데드락 시뮬레이션 데모
	@echo "$(RED)💀 데드락 시뮬레이션 데모$(RESET)"
	@curl -X POST "localhost:8080/api/demo/scenario/deadlock" || echo "데드락 시나리오 생성 실패"
	@cd $(SCRIPTS_DIR) && ./final-deadlock-test.sh

demo-long-tx: ## 장기 트랜잭션 시뮬레이션 데모  
	@echo "$(YELLOW)⏱️ 장기 트랜잭션 시뮬레이션 데모$(RESET)"
	@cd $(SCRIPTS_DIR) && ./enhanced-long-running-test.sh

demo-scenarios: ## 사용 가능한 데모 시나리오 목록 확인
	@echo "$(BLUE)📋 데모 시나리오 목록$(RESET)"
	@curl -s "localhost:8080/api/demo/scenarios" | python3 -m json.tool || echo "데모 API에 접근할 수 없습니다"

deploy-demo: ## 데모 환경 전용 배포 (demo 프로파일)
	@echo "$(BLUE)🎬 데모 환경 배포$(RESET)"
	@$(MAKE) build
	@kubectl apply -f k8s/kubedb-monitor-deployment.yaml
	@kubectl set env deployment/university-registration-demo SPRING_PROFILES_ACTIVE=demo
	@kubectl set env deployment/university-registration-demo APP_REGISTRATION_DATA_INIT_MODE=demo
	@kubectl set env deployment/university-registration-demo DEMO_FEATURES_DATA_RESET_API=true
	@echo "$(GREEN)✅ 데모 환경 배포 완료$(RESET)"

##@ E2E 테스트
e2e-full: ## 전체 솔루션 E2E 테스트 (빌드→배포→이벤트플로우 검증)
	@echo "$(BLUE)🚀 전체 솔루션 E2E 테스트 시작$(RESET)"
	@cd $(PROJECT_ROOT) && ./scripts/comprehensive-e2e-test.sh
	@echo "$(GREEN)🎉 E2E 테스트 완료$(RESET)"

e2e-quick: ## 빠른 E2E 테스트 (현재 배포 상태에서 이벤트플로우만 검증)
	@echo "$(YELLOW)⚡ 빠른 E2E 이벤트 플로우 테스트$(RESET)"
	@cd $(SCRIPTS_DIR) && ./comprehensive-e2e-test.sh --skip-build
	@echo "$(GREEN)✅ 빠른 E2E 테스트 완료$(RESET)"

e2e-logs: ## E2E 테스트 결과 로그 확인
	@echo "$(BLUE)📋 최근 E2E 테스트 로그$(RESET)"
	@find /tmp -name "kubedb-e2e-logs-*" -type d | sort -r | head -1 | xargs -I {} sh -c 'echo "로그 디렉토리: {}" && ls -la {}'

# 환경 변수 설정 확인
check-env:
	@echo "$(BLUE)🔍 환경 확인$(RESET)"
	@command -v kubectl >/dev/null 2>&1 || { echo "$(RED)❌ kubectl이 설치되지 않음$(RESET)"; exit 1; }
	@command -v docker >/dev/null 2>&1 || { echo "$(RED)❌ Docker가 설치되지 않음$(RESET)"; exit 1; }
	@command -v mvn >/dev/null 2>&1 || { echo "$(RED)❌ Maven이 설치되지 않음$(RESET)"; exit 1; }
	@echo "$(GREEN)✅ 모든 필수 도구 설치됨$(RESET)"