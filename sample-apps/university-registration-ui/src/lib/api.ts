import axios from 'axios';
import { Course, EnrollmentRequest, EnrollmentResponse, CartItem, Student } from '@/types/course';

// 클라이언트에서 백엔드 서비스로 직접 접근 (HTTPS 적용)
const API_BASE_URL = typeof window !== 'undefined' 
  ? 'https://university-registration.bitgaram.info' // 클라이언트에서 HTTPS
  : 'http://university-registration-service:8080'; // 서버에서 (SSR) - 정상 작동하는 서비스

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 10000,
});

// 과목 관련 API  
export const coursesApi = {
  // 과목 검색 (페이징 지원)
  searchCourses: (params: {
    page?: number;
    size?: number;
    dept?: number;
    keyword?: string;
  }) => api.get<{
    content: Course[];
    totalElements: number;
    totalPages: number;
    number: number;
    size: number;
  }>('/api/courses', { params }),

  // 과목 상세 정보
  getCourse: (courseId: string) => api.get<Course>(`/api/courses/${courseId}`),

  // 정원이 남은 과목들
  getAvailableCourses: () => api.get<Course[]>('/api/courses/available'),

  // 인기 과목 조회
  getPopularCourses: (threshold: number = 0.8) => 
    api.get<Course[]>('/api/courses/popular', { params: { threshold } }),

  // 학과별 과목 조회
  getCoursesByDepartment: (departmentId: number) => 
    api.get<Course[]>(`/api/courses/department/${departmentId}`),
};

// 장바구니 관련 API
export const cartApi = {
  // 장바구니 조회
  getCart: (studentId: string) => api.get<any>('/api/cart', { params: { studentId } }),

  // 장바구니에 과목 추가
  addToCart: (studentId: string, courseId: string) => 
    api.post<string>('/api/cart/items', { studentId, courseId }),

  // 장바구니에서 과목 제거
  removeFromCart: (studentId: string, courseId: string) => 
    api.delete(`/api/cart/items/${courseId}`, { params: { studentId } }),

  // 장바구니 비우기
  clearCart: (studentId: string) => api.delete('/api/cart', { params: { studentId } }),

  // 장바구니 검증
  validateCart: (studentId: string) => 
    api.post<any>('/api/cart/validate', { studentId }),
};

// 수강신청 관련 API
export const enrollmentApi = {
  // 개별 과목 수강신청
  enroll: (studentId: string, courseId: string) => 
    api.post<EnrollmentResponse>(`/api/enrollments/${courseId}`, null, {
      params: { studentId }
    }),

  // 장바구니에서 수강신청
  enrollFromCart: (studentId: string, courseIds: string[]) => 
    api.post<EnrollmentResponse>('/api/enrollments/from-cart', {
      studentId,
      courseIds,
    }),

  // 수강신청 취소
  withdraw: (studentId: string, courseId: string) => 
    api.delete<string>(`/api/enrollments/${courseId}`, {
      params: { studentId }
    }),

  // 학생의 수강신청 내역 조회
  getEnrollments: (studentId: string) => 
    api.get<any[]>('/api/enrollments/me', { params: { studentId } }),
};

// 학생 관련 API (임시 - 실제로는 인증 시스템과 연동)
export const studentApi = {
  // 현재 학생 정보 (임시)
  getCurrentStudent: () => api.get<Student>('/api/student/current'),
};

export default api;