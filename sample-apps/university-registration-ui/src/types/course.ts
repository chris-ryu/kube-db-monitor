export interface Course {
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
}

export interface EnrollmentRequest {
  studentId: string;
  courseId: string;
}

export interface EnrollmentResponse {
  success: boolean;
  message: string;
  enrollment?: {
    id: string;
    studentId: string;
    courseId: string;
    enrolledAt: string;
  };
}

export interface CartItem {
  id: string;
  studentId: string;
  courseId: string;
  course: Course;
  addedAt: string;
}

export interface Student {
  id: string;
  name: string;
  studentNumber: string;
  department: {
    id: number;
    name: string;
  };
  grade: number;
}