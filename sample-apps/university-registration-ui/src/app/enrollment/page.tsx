'use client';

import { useState, useEffect } from 'react';
import { enrollmentApi } from '@/lib/api';
import { GraduationCap, Calendar, Clock, MapPin, User, BookOpen, AlertTriangle, CheckCircle } from 'lucide-react';

interface Enrollment {
  id: string;
  studentId: string;
  courseId: string;
  course: {
    courseId: string;
    courseName: string;
    professorName: string;
    credits: number;
    maxStudents: number;
    currentEnrollment: number;
    department: {
      id: number;
      name: string;
    };
    schedule: string;
    classroom: string;
    description?: string;
    prerequisites?: string[];
  };
  enrolledAt: string;
  status: string;
}

export default function EnrollmentPage() {
  const [enrollments, setEnrollments] = useState<Enrollment[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const studentId = '2024001'; // 테스트 학생 ID

  useEffect(() => {
    loadEnrollments();
  }, []);

  const loadEnrollments = async () => {
    setLoading(true);
    try {
      const response = await enrollmentApi.getEnrollments(studentId);
      const data = response.data;
        
        // 백엔드에서 받은 데이터를 UI에서 사용할 형태로 변환
        const transformedEnrollments = data.map((enrollment: any) => ({
          id: enrollment.id?.toString() || `enrollment_${enrollment.course?.courseId}`,
          studentId: enrollment.student?.studentId || studentId,
          courseId: enrollment.course?.courseId,
          course: {
            courseId: enrollment.course?.courseId,
            courseName: enrollment.course?.courseName,
            professorName: enrollment.course?.professor,
            credits: enrollment.course?.credits,
            maxStudents: enrollment.course?.capacity,
            currentEnrollment: enrollment.course?.enrolledCount,
            department: {
              id: enrollment.course?.department?.id || 1,
              name: enrollment.course?.department?.departmentName || '미지정'
            },
            schedule: enrollment.course?.dayTime || '',
            classroom: enrollment.course?.classroom,
            description: `${enrollment.course?.courseName} 과목입니다.`,
            prerequisites: enrollment.course?.prerequisiteCourseName ? [enrollment.course.prerequisiteCourseName] : []
          },
          enrolledAt: enrollment.enrolledAt,
          status: 'active'
        }));
        
        setEnrollments(transformedEnrollments);
    } catch (err: any) {
      console.error('수강신청 내역 로드 실패:', err);
      setError(err.message || '수강신청 내역을 불러오는데 실패했습니다.');
      setEnrollments([]);
    } finally {
      setLoading(false);
    }
  };

  const cancelEnrollment = async (courseId: string) => {
    if (!confirm('정말로 이 과목의 수강신청을 취소하시겠습니까?')) return;

    try {
      await enrollmentApi.withdraw(studentId, courseId);
      setEnrollments(prev => prev.filter(enrollment => enrollment.courseId !== courseId));
      setSuccess('수강신청이 취소되었습니다.');
      setTimeout(() => setSuccess(null), 3000);
    } catch (err: any) {
      console.error('수강신청 취소 실패:', err);
      setError(err.message || '수강신청 취소에 실패했습니다.');
      setTimeout(() => setError(null), 3000);
    }
  };

  const getTotalCredits = () => {
    return enrollments.reduce((total, enrollment) => total + enrollment.course.credits, 0);
  };

  if (loading) {
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
          <GraduationCap className="mr-3" size={32} />
          내 수강신청
        </h1>
        <p className="text-gray-600">
          현재 신청한 과목들을 확인하고 관리하세요.
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

      {enrollments.length === 0 ? (
        <div className="text-center py-12">
          <BookOpen className="mx-auto text-gray-400 mb-4" size={64} />
          <h2 className="text-xl font-semibold text-gray-900 mb-2">
            수강신청 내역이 없습니다
          </h2>
          <p className="text-gray-500 mb-6">
            과목 검색 페이지에서 원하는 과목을 신청해보세요.
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
            <h2 className="text-lg font-semibold text-gray-900 mb-4">
              수강신청 현황
            </h2>
            
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-center">
              <div>
                <div className="text-2xl font-bold text-blue-600">
                  {enrollments.length}
                </div>
                <div className="text-sm text-gray-500">신청 과목</div>
              </div>
              <div>
                <div className="text-2xl font-bold text-green-600">
                  {getTotalCredits()}
                </div>
                <div className="text-sm text-gray-500">총 학점</div>
              </div>
              <div>
                <div className="text-2xl font-bold text-purple-600">
                  {enrollments.filter(e => e.course.prerequisites && e.course.prerequisites.length > 0).length}
                </div>
                <div className="text-sm text-gray-500">선수과목 포함</div>
              </div>
            </div>
          </div>

          {/* 수강신청 목록 */}
          <div className="space-y-4">
            {enrollments.map((enrollment) => {
              const course = enrollment.course;
              const enrollmentRate = (course.currentEnrollment / course.maxStudents) * 100;
              
              return (
                <div
                  key={enrollment.id}
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
                          <span className="px-3 py-1 bg-green-100 text-green-800 text-xs font-medium rounded-full">
                            수강중
                          </span>
                        </div>
                      </div>

                      <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-4">
                        <div className="flex items-center text-sm text-gray-600">
                          <User className="mr-2" size={16} />
                          <span>{course.professorName} 교수님</span>
                        </div>
                        <div className="flex items-center text-sm text-gray-600">
                          <Clock className="mr-2" size={16} />
                          <span>
                            {course.schedule ? 
                              course.schedule.split(',').map((time, index) => (
                                <span key={index} className="inline-block bg-blue-50 text-blue-700 px-2 py-1 rounded-md text-xs mr-1">
                                  {time.trim()}
                                </span>
                              )) : 
                              <span className="text-gray-400">시간 미정</span>
                            }
                          </span>
                        </div>
                        <div className="flex items-center text-sm text-gray-600">
                          <MapPin className="mr-2" size={16} />
                          <span>{course.classroom}</span>
                        </div>
                      </div>

                      {/* 신청 일시 */}
                      <div className="mb-4">
                        <div className="flex items-center text-sm text-gray-600">
                          <Calendar className="mr-2" size={16} />
                          <span>신청일시: {new Date(enrollment.enrolledAt).toLocaleString('ko-KR')}</span>
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
                        onClick={() => cancelEnrollment(course.courseId)}
                        className="px-4 py-2 text-red-600 border border-red-200 rounded-lg hover:bg-red-50 text-sm"
                      >
                        수강취소
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