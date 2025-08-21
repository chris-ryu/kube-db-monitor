'use client';

import { useState, useEffect } from 'react';
import { CartItem } from '@/types/course';
import { cartApi, enrollmentApi } from '@/lib/api';
import { Trash2, ShoppingCart, Calendar, Clock, MapPin, AlertTriangle, CheckCircle } from 'lucide-react';

export default function CartPage() {
  const [cartItems, setCartItems] = useState<CartItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [enrolling, setEnrolling] = useState<string[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const studentId = '2024001'; // 테스트 학생 ID

  useEffect(() => {
    loadCart();
  }, []);

  const loadCart = async () => {
    setLoading(true);
    try {
      const response = await cartApi.getCart(studentId);
      // 백엔드 API는 CartSummaryDTO를 반환: { cartItems: [...], totalItems, totalCredits, ... }
      const cartData = response.data;
      
      if (cartData && cartData.cartItems) {
        // CartDTO를 UI에서 기대하는 형태로 변환
        const transformedItems = cartData.cartItems.map((item: any) => ({
          id: item.cartId?.toString() || `cart_${item.course?.courseId}`,
          studentId: item.studentId,
          courseId: item.course?.courseId,
          course: {
            courseId: item.course?.courseId,
            courseName: item.course?.courseName,
            professorName: item.course?.professor, // professor -> professorName
            credits: item.course?.credits,
            maxStudents: item.course?.capacity, // capacity -> maxStudents
            currentEnrollment: item.course?.enrolledCount, // enrolledCount -> currentEnrollment
            department: { 
              id: 1, 
              name: item.course?.departmentName || '미지정' // departmentName -> department.name
            },
            schedule: item.course?.schedule || '', // 백엔드에서 schedule 필드 사용
            classroom: item.course?.classroom,
            description: `${item.course?.courseName} 과목입니다.`,
            prerequisites: item.course?.prerequisiteCourseName ? [item.course.prerequisiteCourseName] : []
          },
          addedAt: item.addedAt
        }));
        setCartItems(transformedItems);
      } else {
        setCartItems([]);
      }
    } catch (err) {
      console.error('장바구니 로드 실패:', err);
      setError('장바구니를 불러오는데 실패했습니다.');
      setCartItems([]);
    } finally {
      setLoading(false);
    }
  };

  const removeFromCart = async (courseId: string) => {
    try {
      await cartApi.removeFromCart(studentId, courseId);
      setCartItems(prev => prev.filter(item => item.courseId !== courseId));
      setSuccess('과목이 장바구니에서 제거되었습니다.');
      setTimeout(() => setSuccess(null), 3000);
    } catch (err) {
      console.error('장바구니 제거 실패:', err);
      setError('과목 제거에 실패했습니다.');
      setTimeout(() => setError(null), 3000);
    }
  };

  const clearCart = async () => {
    if (!confirm('장바구니를 비우시겠습니까?')) return;
    
    try {
      await cartApi.clearCart(studentId);
      setCartItems([]);
      setSuccess('장바구니가 비워졌습니다.');
      setTimeout(() => setSuccess(null), 3000);
    } catch (err) {
      console.error('장바구니 비우기 실패:', err);
      setError('장바구니 비우기에 실패했습니다.');
      setTimeout(() => setError(null), 3000);
    }
  };

  const enrollCourse = async (courseId: string) => {
    setEnrolling(prev => [...prev, courseId]);
    try {
      const response = await enrollmentApi.enroll(studentId, courseId);
      
      if (response.data.success) {
        setCartItems(prev => prev.filter(item => item.courseId !== courseId));
        setSuccess(`수강신청이 완료되었습니다: ${response.data.message}`);
      } else {
        setError(`수강신청 실패: ${response.data.message}`);
      }
      
      setTimeout(() => {
        setSuccess(null);
        setError(null);
      }, 5000);
    } catch (err: any) {
      console.error('수강신청 실패:', err);
      setError(err.response?.data?.message || '수강신청에 실패했습니다.');
      setTimeout(() => setError(null), 5000);
    } finally {
      setEnrolling(prev => prev.filter(id => id !== courseId));
    }
  };

  const enrollAll = async () => {
    if (!confirm(`${cartItems.length}개 과목을 모두 수강신청하시겠습니까?`)) return;
    
    try {
      const courseIds = cartItems.map(item => item.courseId);
      const response = await enrollmentApi.enrollFromCart(studentId, courseIds);
      
      if (response.data.success) {
        setCartItems([]);
        setSuccess(`수강신청이 완료되었습니다: ${response.data.message}`);
      } else {
        setError(`수강신청 실패: ${response.data.message}`);
      }
    } catch (err: any) {
      console.error('일괄 수강신청 실패:', err);
      setError(err.response?.data?.message || '일괄 수강신청에 실패했습니다.');
    }
    
    setTimeout(() => {
      setSuccess(null);
      setError(null);
    }, 5000);
  };

  const getTotalCredits = () => {
    return cartItems.reduce((total, item) => total + item.course.credits, 0);
  };

  const getTimeConflicts = () => {
    const conflicts: string[] = [];
    
    // 각 과목의 시간을 배열로 파싱
    const parseSchedule = (schedule: string): string[] => {
      if (!schedule) return [];
      return schedule.split(',').map(time => time.trim()).filter(time => time.length > 0);
    };
    
    for (let i = 0; i < cartItems.length; i++) {
      for (let j = i + 1; j < cartItems.length; j++) {
        const times1 = parseSchedule(cartItems[i].course.schedule);
        const times2 = parseSchedule(cartItems[j].course.schedule);
        
        // 두 과목의 시간 중 겹치는 것이 있는지 검사
        const hasConflict = times1.some(time1 => 
          times2.some(time2 => time1 === time2)
        );
        
        if (hasConflict) {
          const conflictKey = `${cartItems[i].courseId}-${cartItems[j].courseId}`;
          if (!conflicts.includes(conflictKey)) {
            conflicts.push(conflictKey);
          }
        }
      }
    }
    return conflicts;
  };

  const timeConflicts = getTimeConflicts();

  if (loading && cartItems.length === 0) {
    return (
      <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="flex justify-center items-center py-12">
          <div className="animate-spin rounded-full h-32 w-32 border-b-2 border-blue-500"></div>
        </div>
      </div>
    );
  }

  return (
    <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      {/* 페이지 헤더 */}
      <div className="mb-8">
        <h1 className="text-3xl font-bold text-gray-900 mb-2 flex items-center">
          <ShoppingCart className="mr-3" size={32} />
          장바구니
        </h1>
        <p className="text-gray-600">
          담아둔 과목들을 확인하고 수강신청하세요.
        </p>
      </div>

      {/* 알림 메시지 */}
      {error && (
        <div className="mb-6 p-4 bg-red-50 border border-red-200 rounded-lg flex items-center">
          <AlertTriangle className="text-red-500 mr-2" size={20} />
          <span className="text-red-700">{error}</span>
        </div>
      )}

      {success && (
        <div className="mb-6 p-4 bg-green-50 border border-green-200 rounded-lg flex items-center">
          <CheckCircle className="text-green-500 mr-2" size={20} />
          <span className="text-green-700">{success}</span>
        </div>
      )}

      {cartItems.length === 0 ? (
        <div className="text-center py-12">
          <ShoppingCart className="mx-auto text-gray-400 mb-4" size={64} />
          <h2 className="text-xl font-semibold text-gray-900 mb-2">
            장바구니가 비어있습니다
          </h2>
          <p className="text-gray-500 mb-6">
            과목 검색 페이지에서 원하는 과목을 담아보세요.
          </p>
          <a
            href="/"
            className="inline-flex items-center px-6 py-3 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
          >
            과목 검색하기
          </a>
        </div>
      ) : (
        <>
          {/* 요약 정보 */}
          <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6 mb-6">
            <div className="flex justify-between items-center mb-4">
              <h2 className="text-lg font-semibold text-gray-900">
                수강신청 요약
              </h2>
              <div className="flex space-x-2">
                <button
                  onClick={clearCart}
                  className="px-4 py-2 text-red-600 border border-red-200 rounded-lg hover:bg-red-50"
                >
                  전체 삭제
                </button>
                <button
                  onClick={enrollAll}
                  disabled={enrolling.length > 0 || timeConflicts.length > 0}
                  className="px-6 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  {enrolling.length > 0 ? '신청 중...' : '전체 신청'}
                </button>
              </div>
            </div>
            
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-center">
              <div>
                <div className="text-2xl font-bold text-blue-600">
                  {cartItems.length}
                </div>
                <div className="text-sm text-gray-500">과목 수</div>
              </div>
              <div>
                <div className="text-2xl font-bold text-green-600">
                  {getTotalCredits()}
                </div>
                <div className="text-sm text-gray-500">총 학점</div>
              </div>
              <div>
                <div className={`text-2xl font-bold ${timeConflicts.length > 0 ? 'text-red-600' : 'text-green-600'}`}>
                  {timeConflicts.length}
                </div>
                <div className="text-sm text-gray-500">시간 충돌</div>
              </div>
              <div>
                <div className="text-2xl font-bold text-purple-600">
                  {cartItems.filter(item => item.course.prerequisites && item.course.prerequisites.length > 0).length}
                </div>
                <div className="text-sm text-gray-500">선수과목 필요</div>
              </div>
            </div>

            {timeConflicts.length > 0 && (
              <div className="mt-4 p-3 bg-red-50 border border-red-200 rounded-lg">
                <div className="flex items-center">
                  <AlertTriangle className="text-red-500 mr-2" size={16} />
                  <span className="text-red-700 text-sm font-medium">
                    시간표 충돌이 있습니다. 수강신청 전에 확인해주세요.
                  </span>
                </div>
              </div>
            )}
          </div>

          {/* 장바구니 아이템 목록 */}
          <div className="space-y-4">
            {cartItems.map((item) => {
              const course = item.course;
              const isEnrolling = enrolling.includes(course.courseId);
              const enrollmentRate = (course.currentEnrollment / course.maxStudents) * 100;
              
              return (
                <div
                  key={item.id}
                  className="bg-white rounded-lg shadow-sm border border-gray-200 p-6"
                >
                  <div className="flex justify-between items-start">
                    <div className="flex-1">
                      <div className="flex items-start justify-between mb-4">
                        <div>
                          <h3 className="text-lg font-semibold text-gray-900 mb-1">
                            {course.courseName}
                          </h3>
                          <p className="text-sm text-gray-600">
                            {course.courseId} | {course.department.name} | {course.credits}학점
                          </p>
                        </div>
                        <div className="flex items-center space-x-2">
                          <span className={`px-2 py-1 rounded-full text-xs font-medium ${
                            enrollmentRate >= 100
                              ? 'text-red-600 bg-red-50'
                              : enrollmentRate >= 80
                              ? 'text-orange-600 bg-orange-50'
                              : 'text-green-600 bg-green-50'
                          }`}>
                            {enrollmentRate >= 100 ? '마감' : enrollmentRate >= 80 ? '마감임박' : '여유'}
                          </span>
                        </div>
                      </div>

                      <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-4">
                        <div className="flex items-center text-sm text-gray-600">
                          <Calendar className="mr-2" size={16} />
                          <span>{course.professorName} 교수님</span>
                        </div>
                        <div className="flex items-center text-sm text-gray-600">
                          <Clock className="mr-2" size={16} />
                          <span>{course.schedule}</span>
                        </div>
                        <div className="flex items-center text-sm text-gray-600">
                          <MapPin className="mr-2" size={16} />
                          <span>{course.classroom}</span>
                        </div>
                      </div>

                      {/* 수강신청 현황 */}
                      <div className="mb-4">
                        <div className="flex justify-between items-center mb-2">
                          <span className="text-sm text-gray-600">수강신청 현황</span>
                          <span className="text-sm font-medium">
                            {course.currentEnrollment}/{course.maxStudents}명
                          </span>
                        </div>
                        <div className="w-full bg-gray-200 rounded-full h-2">
                          <div
                            className={`h-2 rounded-full transition-all duration-300 ${
                              enrollmentRate >= 100
                                ? 'bg-red-500'
                                : enrollmentRate >= 80
                                ? 'bg-orange-500'
                                : 'bg-green-500'
                            }`}
                            style={{ width: `${Math.min(enrollmentRate, 100)}%` }}
                          ></div>
                        </div>
                      </div>

                      {/* 선수과목 정보 */}
                      {course.prerequisites && course.prerequisites.length > 0 && (
                        <div className="mb-4">
                          <p className="text-sm text-gray-600 mb-1">선수과목:</p>
                          <div className="flex flex-wrap gap-1">
                            {course.prerequisites.map((prereq, index) => (
                              <span
                                key={index}
                                className="px-2 py-1 bg-yellow-100 text-yellow-800 text-xs rounded-full"
                              >
                                {prereq}
                              </span>
                            ))}
                          </div>
                        </div>
                      )}
                    </div>

                    <div className="flex flex-col space-y-2 ml-6">
                      <button
                        onClick={() => enrollCourse(course.courseId)}
                        disabled={isEnrolling || enrollmentRate >= 100}
                        className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed text-sm"
                      >
                        {isEnrolling ? '신청중...' : '수강신청'}
                      </button>
                      <button
                        onClick={() => removeFromCart(course.courseId)}
                        disabled={isEnrolling}
                        className="px-4 py-2 text-red-600 border border-red-200 rounded-lg hover:bg-red-50 disabled:opacity-50 disabled:cursor-not-allowed text-sm flex items-center justify-center"
                      >
                        <Trash2 size={14} className="mr-1" />
                        제거
                      </button>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        </>
      )}
    </div>
  );
}