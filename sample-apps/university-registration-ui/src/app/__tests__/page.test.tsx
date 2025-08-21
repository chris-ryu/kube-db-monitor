import React from 'react';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import MockAdapter from 'axios-mock-adapter';
import HomePage from '../page';
import api from '@/lib/api';

// Mock the components used in HomePage
jest.mock('@/components/SearchFilters', () => {
  return function MockSearchFilters({ onSearch, loading }: any) {
    return (
      <div data-testid="search-filters">
        <button 
          data-testid="search-button"
          onClick={() => onSearch({ keyword: '프로그래밍', department: '1' })}
          disabled={loading}
        >
          Search
        </button>
      </div>
    );
  };
});

jest.mock('@/components/CourseCard', () => {
  return function MockCourseCard({ course, studentId, isInCart, onCartUpdate }: any) {
    return (
      <div data-testid={`course-card-${course.courseId}`}>
        <h3>{course.courseName}</h3>
        <p>Professor: {course.professorName}</p>
        <p>Credits: {course.credits}</p>
        <button 
          data-testid={`cart-button-${course.courseId}`}
          onClick={onCartUpdate}
        >
          {isInCart ? '장바구니에서 제거' : '장바구니에 추가'}
        </button>
      </div>
    );
  };
});

describe('HomePage Component - API Integration', () => {
  let mock: MockAdapter;

  beforeEach(() => {
    mock = new MockAdapter(api);
    // Reset all mocks
    jest.clearAllMocks();
  });

  afterEach(() => {
    mock.restore();
  });

  const mockCoursesResponse = {
    content: [
      {
        courseId: 'CSE101',
        courseName: '프로그래밍 기초',
        professorName: '김교수',
        credits: 3,
        maxStudents: 30,
        currentEnrollment: 25,
        department: { id: 1, name: '컴퓨터공학과' },
        schedule: '월수금 10:00-11:00',
        classroom: '공학관 201호',
        description: '프로그래밍의 기초를 배우는 과목입니다.',
        prerequisites: []
      },
      {
        courseId: 'MAT101',
        courseName: '미적분학',
        professorName: '박교수',
        credits: 4,
        maxStudents: 40,
        currentEnrollment: 35,
        department: { id: 4, name: '수학과' },
        schedule: '월화수목 09:00-10:00',
        classroom: '자연관 101호',
        description: '미적분학의 기초 이론과 응용을 다룹니다.',
        prerequisites: []
      }
    ],
    totalElements: 2,
    totalPages: 1,
    number: 0,
    size: 20
  };

  const mockCartResponse = [
    { courseId: 'CSE101', courseName: '프로그래밍 기초' }
  ];

  describe('Initial API Calls', () => {
    test('should make correct API calls on mount', async () => {
      // Mock successful API responses
      mock.onGet('/api/courses').reply(200, mockCoursesResponse);
      mock.onGet('/api/cart').reply(200, mockCartResponse);

      await act(async () => {
        render(<HomePage />);
      });

      await waitFor(() => {
        expect(mock.history.get).toHaveLength(2);
      });

      // Check courses API call
      const coursesRequest = mock.history.get.find(req => req.url === '/api/courses');
      expect(coursesRequest).toBeDefined();
      expect(coursesRequest!.params).toEqual({ page: 0, size: 20 });

      // Check cart API call  
      const cartRequest = mock.history.get.find(req => req.url === '/api/cart');
      expect(cartRequest).toBeDefined();
      expect(cartRequest!.params).toEqual({ studentId: '2024001' });

      // Check UI updates
      await waitFor(() => {
        expect(screen.getByText('프로그래밍 기초')).toBeInTheDocument();
        expect(screen.getByText('미적분학')).toBeInTheDocument();
        expect(screen.getByText('총 2개의 과목이 있습니다.')).toBeInTheDocument();
        expect(screen.getByText('장바구니: 1개 과목')).toBeInTheDocument();
      });
    });

    test('should handle courses API error gracefully', async () => {
      // Mock courses API error
      mock.onGet('/api/courses').reply(500, { message: 'Server error' });
      mock.onGet('/api/cart').reply(200, []);

      await act(async () => {
        render(<HomePage />);
      });

      await waitFor(() => {
        expect(screen.getByText('과목 정보를 불러오는데 실패했습니다.')).toBeInTheDocument();
      });

      // Should still display fallback data
      await waitFor(() => {
        expect(screen.getByText('프로그래밍 기초')).toBeInTheDocument();
        expect(screen.getByText('데이터베이스')).toBeInTheDocument();
        expect(screen.getByText('미적분학')).toBeInTheDocument();
      });
    });

    test('should handle cart API error gracefully', async () => {
      mock.onGet('/api/courses').reply(200, mockCoursesResponse);
      mock.onGet('/api/cart').reply(500, { message: 'Cart error' });

      await act(async () => {
        render(<HomePage />);
      });

      await waitFor(() => {
        expect(screen.getByText('장바구니: 0개 과목')).toBeInTheDocument();
      });
    });
  });

  describe('Search Functionality', () => {
    test('should make search API call with correct parameters', async () => {
      // Mock initial API calls
      mock.onGet('/api/courses').reply(200, mockCoursesResponse);
      mock.onGet('/api/cart').reply(200, []);

      await act(async () => {
        render(<HomePage />);
      });

      // Wait for initial load
      await waitFor(() => {
        expect(screen.getByText('프로그래밍 기초')).toBeInTheDocument();
      });

      // Clear mock history for search test
      mock.reset();
      mock.onGet('/api/courses').reply(200, {
        ...mockCoursesResponse,
        content: [mockCoursesResponse.content[0]], // Only programming course
        totalElements: 1
      });

      // Trigger search
      const searchButton = screen.getByTestId('search-button');
      await act(async () => {
        fireEvent.click(searchButton);
      });

      await waitFor(() => {
        expect(mock.history.get).toHaveLength(1);
      });

      const searchRequest = mock.history.get[0];
      expect(searchRequest.url).toBe('/api/courses');
      expect(searchRequest.params).toEqual({
        page: 0,
        size: 20,
        keyword: '프로그래밍',
        dept: 1
      });
    });
  });

  describe('Pagination', () => {
    test('should load more courses when load more button is clicked', async () => {
      const firstPageResponse = {
        content: [mockCoursesResponse.content[0]],
        totalElements: 2,
        totalPages: 2,
        number: 0,
        size: 1
      };

      const secondPageResponse = {
        content: [mockCoursesResponse.content[1]],
        totalElements: 2,
        totalPages: 2,
        number: 1,
        size: 1
      };

      // Mock initial API calls
      mock.onGet('/api/courses').reply(200, firstPageResponse);
      mock.onGet('/api/cart').reply(200, []);

      await act(async () => {
        render(<HomePage />);
      });

      await waitFor(() => {
        expect(screen.getByText('프로그래밍 기초')).toBeInTheDocument();
        expect(screen.getByText('더보기')).toBeInTheDocument();
      });

      // Reset mock and configure for second page
      mock.reset();
      mock.onGet('/api/courses').reply((config) => {
        if (config.params?.page === 1) {
          return [200, secondPageResponse];
        }
        return [404]; // Should not be called
      });

      // Click load more
      const loadMoreButton = screen.getByText('더보기');
      await act(async () => {
        fireEvent.click(loadMoreButton);
      });

      await waitFor(() => {
        expect(screen.getByText('미적분학')).toBeInTheDocument();
      });

      // Check the API call for second page
      expect(mock.history.get).toHaveLength(1);
      const secondPageRequest = mock.history.get[0];
      expect(secondPageRequest.params.page).toBe(1);
    });

    test('should not show load more button when all pages are loaded', async () => {
      const singlePageResponse = {
        content: mockCoursesResponse.content,
        totalElements: 2,
        totalPages: 1,
        number: 0,
        size: 20
      };

      mock.onGet('/api/courses').reply(200, singlePageResponse);
      mock.onGet('/api/cart').reply(200, []);

      await act(async () => {
        render(<HomePage />);
      });

      await waitFor(() => {
        expect(screen.getByText('프로그래밍 기초')).toBeInTheDocument();
      });

      // Should not show load more button
      expect(screen.queryByText('더보기')).not.toBeInTheDocument();
    });
  });

  describe('Cart Integration', () => {
    test('should update cart when cart action is triggered', async () => {
      mock.onGet('/api/courses').reply(200, mockCoursesResponse);
      mock.onGet('/api/cart').reply(200, []);

      await act(async () => {
        render(<HomePage />);
      });

      await waitFor(() => {
        expect(screen.getByText('프로그래밍 기초')).toBeInTheDocument();
      });

      // Mock cart update response
      mock.onGet('/api/cart').reply(200, mockCartResponse);

      // Click cart button
      const cartButton = screen.getByTestId('cart-button-CSE101');
      await act(async () => {
        fireEvent.click(cartButton);
      });

      await waitFor(() => {
        // Should make another API call to get updated cart
        const cartRequests = mock.history.get.filter(req => req.url === '/api/cart');
        expect(cartRequests).toHaveLength(2); // Initial + after update
      });
    });
  });

  describe('Loading States', () => {
    test('should show loading state during initial load', async () => {
      // Delay the API response to test loading state
      mock.onGet('/api/courses').reply(() => {
        return new Promise(resolve => {
          setTimeout(() => resolve([200, mockCoursesResponse]), 100);
        });
      });
      mock.onGet('/api/cart').reply(200, []);

      await act(async () => {
        render(<HomePage />);
      });

      // Should show loading state
      expect(screen.getByText('과목을 불러오는 중...')).toBeInTheDocument();

      // Wait for loading to complete
      await waitFor(() => {
        expect(screen.getByText('프로그래밍 기초')).toBeInTheDocument();
      }, { timeout: 2000 });
    });

    test('should disable search during loading', async () => {
      mock.onGet('/api/courses').reply(200, mockCoursesResponse);
      mock.onGet('/api/cart').reply(200, []);

      await act(async () => {
        render(<HomePage />);
      });

      await waitFor(() => {
        expect(screen.getByText('프로그래밍 기초')).toBeInTheDocument();
      });

      // Mock delayed search response
      mock.reset();
      mock.onGet('/api/courses').reply(() => {
        return new Promise(resolve => {
          setTimeout(() => resolve([200, mockCoursesResponse]), 100);
        });
      });

      const searchButton = screen.getByTestId('search-button');
      await act(async () => {
        fireEvent.click(searchButton);
      });

      // Search button should be disabled during loading
      expect(searchButton).toBeDisabled();
    });
  });

  describe('API Endpoints Validation', () => {
    test('should use correct API endpoints with /api prefix', async () => {
      mock.onGet('/api/courses').reply(200, mockCoursesResponse);
      mock.onGet('/api/cart').reply(200, mockCartResponse);

      await act(async () => {
        render(<HomePage />);
      });

      await waitFor(() => {
        expect(mock.history.get).toHaveLength(2);
      });

      // Verify all requests use /api prefix
      mock.history.get.forEach(request => {
        expect(request.url).toMatch(/^\/api\//);
      });

      // Verify specific endpoints
      const urls = mock.history.get.map(req => req.url);
      expect(urls).toContain('/api/courses');
      expect(urls).toContain('/api/cart');
    });
  });

  describe('Error Recovery', () => {
    test('should retry API calls after error', async () => {
      // First call fails, second succeeds
      mock.onGet('/api/courses').replyOnce(500, { message: 'Server error' });
      mock.onGet('/api/courses').replyOnce(200, mockCoursesResponse);
      mock.onGet('/api/cart').reply(200, []);

      await act(async () => {
        render(<HomePage />);
      });

      // Should show error initially
      await waitFor(() => {
        expect(screen.getByText('과목 정보를 불러오는데 실패했습니다.')).toBeInTheDocument();
      });

      // Trigger search to retry
      mock.onGet('/api/courses').reply(200, mockCoursesResponse);
      
      const searchButton = screen.getByTestId('search-button');
      await act(async () => {
        fireEvent.click(searchButton);
      });

      // Should clear error and show results
      await waitFor(() => {
        expect(screen.queryByText('과목 정보를 불러오는데 실패했습니다.')).not.toBeInTheDocument();
        expect(screen.getByText('프로그래밍 기초')).toBeInTheDocument();
      });
    });
  });
});