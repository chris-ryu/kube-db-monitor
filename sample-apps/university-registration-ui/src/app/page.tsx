'use client';

import { useState, useEffect } from 'react';
import { Course } from '@/types/course';
import { coursesApi, cartApi } from '@/lib/api';
import SearchFilters from '@/components/SearchFilters';
import CourseCard from '@/components/CourseCard';
import { Loader2, AlertCircle } from 'lucide-react';

export default function HomePage() {
  const [courses, setCourses] = useState<Course[]>([]);
  const [cartItems, setCartItems] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [pagination, setPagination] = useState({
    current: 0,
    total: 0,
    totalPages: 0,
    size: 20,
  });

  const studentId = '2024001'; // 테스트 학생 ID

  // 초기 데이터 로드
  useEffect(() => {
    loadCourses();
    loadCart();
  }, []);

  const loadCourses = async (params?: any) => {
    setLoading(true);
    setError(null);
    
    try {
      const response = await coursesApi.searchCourses({
        page: 0,
        size: 20,
        ...params,
      });

      setCourses(response.data.content);
      setPagination({
        current: response.data.number,
        total: response.data.totalElements,
        totalPages: response.data.totalPages,
        size: response.data.size,
      });
    } catch (err: any) {
      console.error('과목 로드 실패:', err);
      setError('과목 정보를 불러오는데 실패했습니다.');
      
      // 테스트용 더미 데이터
      setCourses([
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
          courseId: 'CSE201',
          courseName: '데이터베이스',
          professorName: '이교수',
          credits: 3,
          maxStudents: 25,
          currentEnrollment: 24,
          department: { id: 1, name: '컴퓨터공학과' },
          schedule: '화목 14:00-15:30',
          classroom: '공학관 301호',
          description: '데이터베이스 설계와 운용을 학습합니다.',
          prerequisites: ['CSE101']
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
      ]);
      setPagination({
        current: 0,
        total: 3,
        totalPages: 1,
        size: 20,
      });
    } finally {
      setLoading(false);
    }
  };

  const loadCart = async () => {
    try {
      const response = await cartApi.getCart(studentId);
      // Cart API는 CartSummaryDTO를 반환: { cartItems: [...], totalItems, ... }
      const cartData = response.data;
      if (cartData && cartData.cartItems && Array.isArray(cartData.cartItems)) {
        setCartItems(cartData.cartItems.map((item: any) => item.course?.courseId || item.courseId));
      } else {
        setCartItems([]); // 빈 장바구니로 초기화
      }
    } catch (err) {
      console.error('장바구니 로드 실패:', err);
      setCartItems([]); // 빈 장바구니로 초기화
    }
  };

  const handleSearch = (filters: any) => {
    const params: any = {};
    
    if (filters.keyword) params.keyword = filters.keyword;
    if (filters.department) params.dept = parseInt(filters.department);
    
    loadCourses(params);
  };

  const handleCartUpdate = () => {
    loadCart();
  };

  const loadMore = async () => {
    if (pagination.current >= pagination.totalPages - 1) return;
    
    setLoading(true);
    try {
      const response = await coursesApi.searchCourses({
        page: pagination.current + 1,
        size: pagination.size,
      });

      setCourses(prev => [...prev, ...response.data.content]);
      setPagination({
        current: response.data.number,
        total: response.data.totalElements,
        totalPages: response.data.totalPages,
        size: response.data.size,
      });
    } catch (err) {
      console.error('추가 과목 로드 실패:', err);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      {/* 페이지 헤더 */}
      <div className="mb-8">
        <h1 className="text-3xl font-bold text-gray-900 mb-2">
          과목 검색
        </h1>
        <p className="text-gray-600">
          수강하고 싶은 과목을 검색하고 장바구니에 담아보세요.
        </p>
      </div>

      {/* 검색 필터 */}
      <SearchFilters onSearch={handleSearch} loading={loading} />

      {/* 검색 결과 헤더 */}
      <div className="flex justify-between items-center mb-6">
        <div>
          <h2 className="text-lg font-semibold text-gray-900">
            검색 결과
          </h2>
          <p className="text-sm text-gray-600">
            총 {pagination.total}개의 과목이 있습니다.
          </p>
        </div>
        <div className="text-sm text-gray-500">
          장바구니: {cartItems.length}개 과목
        </div>
      </div>

      {/* 오류 메시지 */}
      {error && (
        <div className="mb-6 p-4 bg-red-50 border border-red-200 rounded-lg flex items-center">
          <AlertCircle className="text-red-500 mr-2" size={20} />
          <span className="text-red-700">{error}</span>
        </div>
      )}

      {/* 과목 목록 */}
      {loading && courses.length === 0 ? (
        <div className="flex justify-center items-center py-12">
          <Loader2 className="animate-spin text-blue-500" size={32} />
          <span className="ml-2 text-gray-600">과목을 불러오는 중...</span>
        </div>
      ) : courses.length === 0 ? (
        <div className="text-center py-12">
          <p className="text-gray-500 text-lg">검색 결과가 없습니다.</p>
          <p className="text-gray-400 text-sm mt-2">다른 검색어로 시도해보세요.</p>
        </div>
      ) : (
        <div className="space-y-6">
          <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
            {courses.map((course) => (
              <CourseCard
                key={course.courseId}
                course={course}
                studentId={studentId}
                isInCart={cartItems.includes(course.courseId)}
                onCartUpdate={handleCartUpdate}
              />
            ))}
          </div>

          {/* 더보기 버튼 */}
          {pagination.current < pagination.totalPages - 1 && (
            <div className="text-center">
              <button
                onClick={loadMore}
                disabled={loading}
                className="px-6 py-3 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 focus:ring-2 focus:ring-blue-500 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {loading ? (
                  <div className="flex items-center">
                    <Loader2 className="animate-spin mr-2" size={16} />
                    로딩중...
                  </div>
                ) : (
                  '더보기'
                )}
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}