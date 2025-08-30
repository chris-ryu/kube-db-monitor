import axios from 'axios';
import { Course, EnrollmentRequest, EnrollmentResponse, CartItem, Student } from '@/types/course';

// Next.js API Routes를 통한 프록시 방식 (서버사이드)
const API_BASE_URL = '/api'; // 모든 요청이 Next.js API Routes를 통함

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
  }>('/courses', { params }),

  // 과목 상세 정보
  getCourse: (courseId: string) => api.get<Course>(`/courses/${courseId}`),

  // 정원이 남은 과목들
  getAvailableCourses: () => api.get<Course[]>('/courses/available'),

  // 인기 과목 조회
  getPopularCourses: (threshold: number = 0.8) => 
    api.get<Course[]>('/courses/popular', { params: { threshold } }),

  // 학과별 과목 조회
  getCoursesByDepartment: (departmentId: number) => 
    api.get<Course[]>(`/courses/department/${departmentId}`),
};

// 장바구니 관련 API
export const cartApi = {
  // 장바구니 조회
  getCart: (studentId: string) => api.get<any>('/cart', { params: { studentId } }),

  // 장바구니에 과목 추가
  addToCart: (studentId: string, courseId: string) => 
    api.post<string>('/cart/items', { studentId, courseId }),

  // 장바구니에서 과목 제거
  removeFromCart: (studentId: string, courseId: string) => 
    api.delete(`/cart/items/${courseId}`, { params: { studentId } }),

  // 장바구니 비우기
  clearCart: (studentId: string) => api.delete('/cart', { params: { studentId } }),

  // 장바구니 검증
  validateCart: (studentId: string) => 
    api.post<any>('/cart/validate', { studentId }),
};

// 수강신청 관련 API
export const enrollmentApi = {
  // 개별 과목 수강신청
  enroll: (studentId: string, courseId: string) => 
    api.post<EnrollmentResponse>(`/enrollments/${courseId}`, null, {
      params: { studentId }
    }),

  // 장바구니에서 수강신청
  enrollFromCart: (studentId: string, courseIds: string[]) => 
    api.post<EnrollmentResponse>('/enrollments', {
      studentId,
      courseIds,
    }),

  // 수강신청 취소
  withdraw: (studentId: string, courseId: string) => 
    api.delete<string>(`/enrollments/${courseId}`, {
      params: { studentId }
    }),

  // 학생의 수강신청 내역 조회
  getEnrollments: (studentId: string) => 
    api.get<any[]>('/enrollments/me', { params: { studentId } }),
};

// 학생 관련 API (임시 - 실제로는 인증 시스템과 연동)
export const studentApi = {
  // 현재 학생 정보 (임시)
  getCurrentStudent: () => api.get<Student>('/student/current'),
};

export default api;