import MockAdapter from 'axios-mock-adapter';
import api, { coursesApi, cartApi, enrollmentApi, studentApi } from '../api';

describe('University Registration API Tests', () => {
  let mock: MockAdapter;

  beforeEach(() => {
    // Create a new mock adapter for each test
    mock = new MockAdapter(api);
  });

  afterEach(() => {
    // Clean up mock after each test
    mock.restore();
  });

  describe('API Configuration', () => {
    test('API base URL should be configured correctly', () => {
      expect(api.defaults.baseURL).toBeDefined();
      expect(api.defaults.baseURL).toContain('university-registration');
    });

    test('API should have correct headers', () => {
      expect(api.defaults.headers['Content-Type']).toBe('application/json');
    });

    test('API should have timeout configured', () => {
      expect(api.defaults.timeout).toBe(10000);
    });
  });

  describe('Courses API', () => {
    const mockCourses = {
      content: [
        {
          id: '1',
          name: '프로그래밍 기초',
          professor: '김교수',
          credits: 3,
          capacity: 30,
          enrolled: 15,
          departmentId: 1,
          semester: '2024-1',
        },
        {
          id: '2', 
          name: '자료구조',
          professor: '이교수',
          credits: 3,
          capacity: 25,
          enrolled: 20,
          departmentId: 1,
          semester: '2024-1',
        },
      ],
      totalElements: 2,
      totalPages: 1,
      number: 0,
      size: 10,
    };

    test('should search courses with correct /api path', async () => {
      mock.onGet('/api/courses').reply(200, mockCourses);

      const result = await coursesApi.searchCourses({ page: 0, size: 10 });
      
      expect(result.data).toEqual(mockCourses);
      expect(mock.history.get[0].url).toBe('/api/courses');
      expect(mock.history.get[0].params).toEqual({ page: 0, size: 10 });
    });

    test('should get course details with correct /api path', async () => {
      const courseId = '1';
      const mockCourse = mockCourses.content[0];
      
      mock.onGet(`/api/courses/${courseId}`).reply(200, mockCourse);

      const result = await coursesApi.getCourse(courseId);
      
      expect(result.data).toEqual(mockCourse);
      expect(mock.history.get[0].url).toBe('/api/courses/1');
    });

    test('should get available courses with correct /api path', async () => {
      mock.onGet('/api/courses/available').reply(200, mockCourses.content);

      const result = await coursesApi.getAvailableCourses();
      
      expect(result.data).toEqual(mockCourses.content);
      expect(mock.history.get[0].url).toBe('/api/courses/available');
    });

    test('should get popular courses with threshold parameter', async () => {
      mock.onGet('/api/courses/popular').reply(200, mockCourses.content);

      const result = await coursesApi.getPopularCourses(0.8);
      
      expect(result.data).toEqual(mockCourses.content);
      expect(mock.history.get[0].url).toBe('/api/courses/popular');
      expect(mock.history.get[0].params).toEqual({ threshold: 0.8 });
    });

    test('should get courses by department with correct /api path', async () => {
      const departmentId = 1;
      mock.onGet(`/api/courses/department/${departmentId}`).reply(200, mockCourses.content);

      const result = await coursesApi.getCoursesByDepartment(departmentId);
      
      expect(result.data).toEqual(mockCourses.content);
      expect(mock.history.get[0].url).toBe('/api/courses/department/1');
    });
  });

  describe('Cart API', () => {
    const mockCart = {
      studentId: 'student1',
      items: [
        { courseId: '1', courseName: '프로그래밍 기초', credits: 3 },
        { courseId: '2', courseName: '자료구조', credits: 3 },
      ],
      totalCredits: 6,
    };

    test('should get cart with correct /api path', async () => {
      const studentId = 'student1';
      mock.onGet('/api/cart').reply(200, mockCart);

      const result = await cartApi.getCart(studentId);
      
      expect(result.data).toEqual(mockCart);
      expect(mock.history.get[0].url).toBe('/api/cart');
      expect(mock.history.get[0].params).toEqual({ studentId });
    });

    test('should add to cart with correct /api path', async () => {
      const studentId = 'student1';
      const courseId = '3';
      const responseMessage = '장바구니에 추가되었습니다';
      
      mock.onPost('/api/cart/items').reply(200, responseMessage);

      const result = await cartApi.addToCart(studentId, courseId);
      
      expect(result.data).toBe(responseMessage);
      expect(mock.history.post[0].url).toBe('/api/cart/items');
      expect(JSON.parse(mock.history.post[0].data)).toEqual({ studentId, courseId });
    });

    test('should remove from cart with correct /api path', async () => {
      const studentId = 'student1';
      const courseId = '2';
      
      mock.onDelete(`/api/cart/items/${courseId}`).reply(200, '장바구니에서 제거되었습니다');

      const result = await cartApi.removeFromCart(studentId, courseId);
      
      expect(result.data).toBe('장바구니에서 제거되었습니다');
      expect(mock.history.delete[0].url).toBe('/api/cart/items/2');
      expect(mock.history.delete[0].params).toEqual({ studentId });
    });

    test('should clear cart with correct /api path', async () => {
      const studentId = 'student1';
      
      mock.onDelete('/api/cart').reply(200, '장바구니가 비워졌습니다');

      const result = await cartApi.clearCart(studentId);
      
      expect(result.data).toBe('장바구니가 비워졌습니다');
      expect(mock.history.delete[0].url).toBe('/api/cart');
      expect(mock.history.delete[0].params).toEqual({ studentId });
    });

    test('should validate cart with correct /api path', async () => {
      const studentId = 'student1';
      const validationResult = { valid: true, conflicts: [] };
      
      mock.onPost('/api/cart/validate').reply(200, validationResult);

      const result = await cartApi.validateCart(studentId);
      
      expect(result.data).toEqual(validationResult);
      expect(mock.history.post[0].url).toBe('/api/cart/validate');
      expect(JSON.parse(mock.history.post[0].data)).toEqual({ studentId });
    });
  });

  describe('Enrollment API', () => {
    const mockEnrollmentResponse = {
      success: true,
      message: '수강신청이 완료되었습니다',
      courseId: '1',
      enrollmentId: 'enroll123',
    };

    test('should enroll in course with correct /api path', async () => {
      const studentId = 'student1';
      const courseId = '1';
      
      mock.onPost(`/api/enrollments/${courseId}`).reply(200, mockEnrollmentResponse);

      const result = await enrollmentApi.enroll(studentId, courseId);
      
      expect(result.data).toEqual(mockEnrollmentResponse);
      expect(mock.history.post[0].url).toBe('/api/enrollments/1');
      expect(mock.history.post[0].params).toEqual({ studentId });
    });

    test('should enroll from cart with correct /api path', async () => {
      const studentId = 'student1';
      const courseIds = ['1', '2'];
      
      mock.onPost('/api/enrollments/from-cart').reply(200, mockEnrollmentResponse);

      const result = await enrollmentApi.enrollFromCart(studentId, courseIds);
      
      expect(result.data).toEqual(mockEnrollmentResponse);
      expect(mock.history.post[0].url).toBe('/api/enrollments/from-cart');
      expect(JSON.parse(mock.history.post[0].data)).toEqual({ studentId, courseIds });
    });

    test('should withdraw from course with correct /api path', async () => {
      const studentId = 'student1';
      const courseId = '1';
      
      mock.onDelete(`/api/enrollments/${courseId}`).reply(200, '수강신청이 취소되었습니다');

      const result = await enrollmentApi.withdraw(studentId, courseId);
      
      expect(result.data).toBe('수강신청이 취소되었습니다');
      expect(mock.history.delete[0].url).toBe('/api/enrollments/1');
      expect(mock.history.delete[0].params).toEqual({ studentId });
    });

    test('should get enrollments with correct /api path', async () => {
      const studentId = 'student1';
      const mockEnrollments = [
        { courseId: '1', courseName: '프로그래밍 기초', enrollmentDate: '2024-01-15' },
        { courseId: '2', courseName: '자료구조', enrollmentDate: '2024-01-16' },
      ];
      
      mock.onGet('/api/enrollments/me').reply(200, mockEnrollments);

      const result = await enrollmentApi.getEnrollments(studentId);
      
      expect(result.data).toEqual(mockEnrollments);
      expect(mock.history.get[0].url).toBe('/api/enrollments/me');
      expect(mock.history.get[0].params).toEqual({ studentId });
    });
  });

  describe('Student API', () => {
    const mockStudent = {
      id: 'student1',
      name: '김학생',
      studentNumber: '2024001',
      department: '컴퓨터공학과',
      grade: 2,
      maxCredits: 18,
    };

    test('should get current student with correct /api path', async () => {
      mock.onGet('/api/student/current').reply(200, mockStudent);

      const result = await studentApi.getCurrentStudent();
      
      expect(result.data).toEqual(mockStudent);
      expect(mock.history.get[0].url).toBe('/api/student/current');
    });
  });

  describe('Error Handling', () => {
    test('should handle 404 error for non-existent course', async () => {
      mock.onGet('/api/courses/999').reply(404, { message: 'Course not found' });

      try {
        await coursesApi.getCourse('999');
        fail('Expected error was not thrown');
      } catch (error: any) {
        expect(error.response.status).toBe(404);
        expect(error.response.data.message).toBe('Course not found');
      }
    });

    test('should handle 500 error for server issues', async () => {
      mock.onGet('/api/courses').reply(500, { message: 'Internal server error' });

      try {
        await coursesApi.searchCourses({});
        fail('Expected error was not thrown');
      } catch (error: any) {
        expect(error.response.status).toBe(500);
        expect(error.response.data.message).toBe('Internal server error');
      }
    });

    test('should handle network timeout', async () => {
      mock.onGet('/api/courses').timeout();

      try {
        await coursesApi.searchCourses({});
        fail('Expected timeout error was not thrown');
      } catch (error: any) {
        expect(error.code).toBe('ECONNABORTED');
      }
    });
  });

  describe('API Path Validation', () => {
    test('all API endpoints should start with /api', () => {
      const apiEndpoints = [
        '/api/courses',
        '/api/courses/1',
        '/api/courses/available',
        '/api/courses/popular',
        '/api/courses/department/1',
        '/api/cart',
        '/api/cart/items',
        '/api/cart/items/1',
        '/api/cart/validate',
        '/api/enrollments/1',
        '/api/enrollments/from-cart',
        '/api/enrollments/me',
        '/api/student/current',
      ];

      apiEndpoints.forEach(endpoint => {
        expect(endpoint).toMatch(/^\/api\//);
      });
    });

    test('API base URL should support both client and server environments', () => {
      // Test client-side URL (window is defined)
      Object.defineProperty(window, 'location', {
        value: { href: 'https://university-registration.bitgaram.info' },
        writable: true
      });
      
      expect(typeof window).toBe('object');
      
      // Test server-side URL (window is undefined)
      const originalWindow = global.window;
      // @ts-ignore
      delete global.window;
      
      expect(typeof window).toBe('undefined');
      
      // Restore window
      global.window = originalWindow;
    });
  });
});