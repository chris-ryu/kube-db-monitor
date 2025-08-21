# University Registration UI

React + Next.js로 구현된 수강신청 시스템의 사용자 인터페이스입니다.

## 🏗️ 아키텍처

- **Frontend**: React 18 + Next.js 14
- **Styling**: Tailwind CSS
- **API Communication**: Axios
- **Testing**: Jest + React Testing Library
- **TypeScript**: 완전한 타입 안정성

## 🚀 기능

- 📚 과목 검색 및 필터링
- 🛒 장바구니 기능 
- ✅ 수강신청 처리
- 📊 실시간 수강신청 현황 확인
- 🧪 **완전한 테스트 수트** (API 경로 매핑 검증 포함)

## 🧪 테스트

### 테스트 종류

1. **Unit Tests**: API 클라이언트와 컴포넌트 로직 테스트
2. **Integration Tests**: UI 컴포넌트와 API 통신 테스트  
3. **E2E Tests**: 실제 서버와의 통신 테스트

### 테스트 실행

```bash
# 모든 테스트 실행
npm test

# 테스트 watch 모드
npm run test:watch

# 커버리지 리포트 생성
npm run test:coverage

# API 관련 테스트만 실행
npm run test:api
```

### 🔍 API 경로 매핑 테스트

모든 테스트에서 다음을 검증합니다:

- ✅ 모든 API 호출이 `/api` 프리픽스를 사용
- ✅ Ingress 라우팅과 매치되는 올바른 경로
- ✅ REST API 서버의 context-path와 일치
- ✅ 에러 처리 및 폴백 메커니즘

```typescript
// 예시: API 경로 검증 테스트
test('should use correct /api path', async () => {
  mock.onGet('/api/courses').reply(200, mockData);
  
  await coursesApi.searchCourses({});
  
  expect(mock.history.get[0].url).toBe('/api/courses');
});
```

## 🚀 개발 환경 설정

### 설치 및 실행

```bash
# 의존성 설치
npm install

# 개발 서버 실행
npm run dev

# 프로덕션 빌드
npm run build

# 프로덕션 서버 실행  
npm start
```

### 환경 변수

```env
# .env.local 파일 생성
NEXT_PUBLIC_API_URL=https://university-registration.bitgaram.info
SKIP_E2E_TESTS=false  # E2E 테스트 건너뛰기 여부
```

## Docker 배포

### 이미지 빌드

```bash
docker build -t university-registration-ui .
```

### 컨테이너 실행

```bash
docker run -p 3000:3000 \
  -e UNIVERSITY_API_URL=http://your-api-server \
  university-registration-ui
```

## Kubernetes 배포

전체 수강신청 시스템(API + UI)을 Kubernetes에 배포:

```bash
kubectl apply -f k8s/university-registration-with-ui.yaml
```

## 📡 API 통신

### API 클라이언트 구성

```typescript
// src/lib/api.ts
const API_BASE_URL = typeof window !== 'undefined' 
  ? 'https://university-registration.bitgaram.info'  // 클라이언트
  : 'http://university-registration-service:8080';   // SSR

const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000,
});
```

### API 엔드포인트

| 기능 | 엔드포인트 | 메서드 | 설명 |
|------|-----------|--------|------|
| 과목 검색 | `/api/courses` | GET | 페이징된 과목 목록 |
| 과목 상세 | `/api/courses/{id}` | GET | 특정 과목 정보 |
| 인기 과목 | `/api/courses/popular` | GET | 인기 과목 목록 |
| 장바구니 조회 | `/api/cart` | GET | 학생 장바구니 |
| 장바구니 추가 | `/api/cart/items` | POST | 과목을 장바구니에 추가 |
| 수강 신청 | `/api/enrollments/{id}` | POST | 과목 수강신청 |
| 수강 취소 | `/api/enrollments/{id}` | DELETE | 수강신청 취소 |

### 에러 처리

```typescript
try {
  const response = await coursesApi.searchCourses(params);
  setCourses(response.data.content);
} catch (error) {
  console.error('API 호출 실패:', error);
  setError('데이터를 불러오는데 실패했습니다.');
  
  // 폴백 데이터 표시
  setCourses(fallbackData);
}
```

## 폴더 구조

```
src/
├── app/                    # Next.js App Router
│   ├── page.tsx           # 메인 페이지 (과목 검색)
│   ├── cart/              # 장바구니 페이지
│   ├── api/health/        # Health check API
│   └── __tests__/         # 페이지 컴포넌트 테스트
├── components/            # 재사용 가능한 컴포넌트
│   ├── CourseCard.tsx     # 과목 카드 컴포넌트
│   └── SearchFilters.tsx  # 검색 필터 컴포넌트
├── types/                 # TypeScript 타입 정의
├── lib/                   # 유틸리티 및 API 클라이언트
│   ├── api.ts            # API 클라이언트
│   └── __tests__/        # API 클라이언트 테스트
├── __tests__/            # E2E 통합 테스트
└── hooks/                # 커스텀 React 훅
```

## 모니터링

이 애플리케이션은 KubeDB Monitor와 연동되어 다음을 모니터링합니다:

- API 응답 시간
- 데이터베이스 쿼리 성능
- 수강신청 트랜잭션 상태
- 동시 접속자 수

## 🔧 개발 가이드

### 새로운 API 엔드포인트 추가

1. `src/lib/api.ts`에 API 함수 추가:
```typescript
export const newApi = {
  getData: () => api.get<DataType>('/api/new-endpoint'),
};
```

2. **반드시 테스트 작성**:
```typescript
// src/lib/__tests__/api.test.ts
test('should call new endpoint with correct /api path', async () => {
  mock.onGet('/api/new-endpoint').reply(200, mockData);
  
  const result = await newApi.getData();
  
  expect(result.data).toEqual(mockData);
  expect(mock.history.get[0].url).toBe('/api/new-endpoint');
});
```

3. 컴포넌트에서 사용:
```typescript
const [data, setData] = useState();

useEffect(() => {
  newApi.getData()
    .then(response => setData(response.data))
    .catch(error => console.error(error));
}, []);
```

### 테스트 작성 가이드

#### ✅ 올바른 API 테스트

```typescript
test('should use correct API path', async () => {
  mock.onGet('/api/courses').reply(200, mockData);
  
  await coursesApi.searchCourses({});
  
  expect(mock.history.get[0].url).toBe('/api/courses');
});
```

#### ❌ 잘못된 API 테스트

```typescript
test('should get courses', async () => {
  mock.onGet().reply(200, mockData);  // 경로 검증 누락!
  
  const result = await coursesApi.searchCourses({});
  
  expect(result.data).toEqual(mockData);
});
```

### E2E 테스트 환경 설정

```bash
# E2E 테스트 건너뛰기 (CI 환경에서)
SKIP_E2E_TESTS=true npm test

# 실제 서버 대상 E2E 테스트
SKIP_E2E_TESTS=false NEXT_PUBLIC_API_URL=http://localhost:8080 npm test
```

## 🐛 트러블슈팅

### 일반적인 문제들

1. **API 연결 실패**
   ```
   Error: Network Error
   ```
   - REST API 서버가 실행 중인지 확인
   - API_BASE_URL 환경변수 확인
   - CORS 설정 확인

2. **테스트 실패**
   ```
   Error: Request failed with status code 404
   ```
   - API 경로에 `/api` 프리픽스가 있는지 확인
   - Ingress 설정과 매치되는지 확인

3. **타입스크립트 오류**
   ```
   Error: Property 'courseId' does not exist
   ```
   - `src/types/course.ts`에서 타입 정의 확인
   - API 응답 구조와 타입이 일치하는지 확인

### 디버깅

```typescript
// API 호출 디버깅
api.interceptors.request.use(request => {
  console.log('API Request:', request);
  return request;
});

api.interceptors.response.use(
  response => {
    console.log('API Response:', response);
    return response;
  },
  error => {
    console.error('API Error:', error.response?.data);
    return Promise.reject(error);
  }
);
```

## 🤝 기여하기

1. API 변경 시 **반드시 테스트 업데이트**
2. 새로운 엔드포인트는 **`/api` 프리픽스 사용**
3. E2E 테스트는 실제 서버 가용성을 고려하여 작성
4. 타입 정의는 백엔드 API 스펙과 동기화

---

**참고**: 이 UI는 KubeDB Monitor 프로젝트의 일부로, REST API 서버와 함께 동작합니다. API 서버의 context-path가 `/api`로 설정되어 있어서 모든 API 호출이 `/api` 경로를 사용합니다.