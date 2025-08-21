'use client';

import { useState } from 'react';
import { Course } from '@/types/course';
import { Users, Clock, MapPin, Star, Plus, Minus } from 'lucide-react';
import { cartApi } from '@/lib/api';

interface CourseCardProps {
  course: Course;
  studentId?: string;
  isInCart?: boolean;
  onCartUpdate?: () => void;
}

export default function CourseCard({ 
  course, 
  studentId = '2024001', // 임시 학생 ID
  isInCart = false,
  onCartUpdate 
}: CourseCardProps) {
  const [loading, setLoading] = useState(false);
  const [inCart, setInCart] = useState(isInCart);

  const availableSlots = course.maxStudents - course.currentEnrollment;
  const enrollmentRate = (course.currentEnrollment / course.maxStudents) * 100;

  const getEnrollmentStatus = () => {
    if (enrollmentRate >= 100) return { status: 'full', color: 'text-red-600 bg-red-50' };
    if (enrollmentRate >= 80) return { status: 'limited', color: 'text-orange-600 bg-orange-50' };
    return { status: 'available', color: 'text-green-600 bg-green-50' };
  };

  const enrollmentStatus = getEnrollmentStatus();

  const handleCartAction = async () => {
    setLoading(true);
    try {
      if (inCart) {
        await cartApi.removeFromCart(studentId, course.courseId);
        setInCart(false);
      } else {
        await cartApi.addToCart(studentId, course.courseId);
        setInCart(true);
      }
      onCartUpdate?.();
    } catch (error) {
      console.error('장바구니 업데이트 실패:', error);
      alert('장바구니 업데이트에 실패했습니다.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6 hover:shadow-md transition-shadow">
      {/* 과목 헤더 */}
      <div className="flex justify-between items-start mb-4">
        <div>
          <h3 className="text-lg font-semibold text-gray-900 mb-1">
            {course.courseName}
          </h3>
          <p className="text-sm text-gray-600">
            {course.courseId} | {course.department.name}
          </p>
        </div>
        <div className="flex items-center space-x-2">
          <span className={`px-2 py-1 rounded-full text-xs font-medium ${enrollmentStatus.color}`}>
            {enrollmentStatus.status === 'full' && '마감'}
            {enrollmentStatus.status === 'limited' && '마감임박'}
            {enrollmentStatus.status === 'available' && '여유'}
          </span>
          <button
            onClick={handleCartAction}
            disabled={loading || enrollmentStatus.status === 'full'}
            className={`p-2 rounded-full transition-colors ${
              inCart
                ? 'bg-red-100 text-red-600 hover:bg-red-200'
                : 'bg-blue-100 text-blue-600 hover:bg-blue-200'
            } ${loading || enrollmentStatus.status === 'full' ? 'opacity-50 cursor-not-allowed' : ''}`}
          >
            {inCart ? <Minus size={16} /> : <Plus size={16} />}
          </button>
        </div>
      </div>

      {/* 과목 정보 */}
      <div className="space-y-2 mb-4">
        <div className="flex items-center text-sm text-gray-600">
          <Users size={16} className="mr-2" />
          <span>
            {course.professorName} 교수님 | {course.credits}학점
          </span>
        </div>
        
        <div className="flex items-center text-sm text-gray-600">
          <Clock size={16} className="mr-2" />
          <span>
            {course.schedule ? 
              course.schedule.split(',').map((time, index) => (
                <span key={index} className="inline-block bg-blue-50 text-blue-700 px-2 py-1 rounded-md text-xs mr-1 mb-1">
                  {time.trim()}
                </span>
              )) : 
              <span className="text-gray-400">시간 미정</span>
            }
          </span>
        </div>

        <div className="flex items-center text-sm text-gray-600">
          <MapPin size={16} className="mr-2" />
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
        <p className="text-xs text-gray-500 mt-1">
          {availableSlots > 0 ? `${availableSlots}자리 남음` : '마감됨'}
        </p>
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

      {/* 과목 설명 */}
      {course.description && (
        <p className="text-sm text-gray-600 line-clamp-2">
          {course.description}
        </p>
      )}
    </div>
  );
}