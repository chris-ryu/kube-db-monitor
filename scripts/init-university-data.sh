#!/bin/bash

set -e

echo "📚 University Registration 데이터베이스 초기화 시작"

# PostgreSQL 연결 정보
POSTGRES_HOST="postgres-cluster-rw.postgres-system"
POSTGRES_PORT="5432"
POSTGRES_DB="university"
POSTGRES_USER="univ-app"
POSTGRES_PASSWORD="qlcrkfka1#"

# kubectl을 통해 PostgreSQL 클러스터에 연결
echo "🔍 PostgreSQL 연결 확인..."
kubectl exec -n postgres-system postgres-cluster-1 -- psql -U postgres -d university -c "\dt"

echo "🏗️ 기본 테이블 생성 중..."

# SQL 스크립트 실행
kubectl exec -n postgres-system postgres-cluster-1 -- psql -U postgres -d university << 'EOF'
-- Departments 테이블
CREATE TABLE IF NOT EXISTS departments (
    department_id BIGSERIAL PRIMARY KEY,
    code VARCHAR(10) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL
);

-- Students 테이블
CREATE TABLE IF NOT EXISTS students (
    student_id BIGSERIAL PRIMARY KEY,
    student_number VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL,
    department_id BIGINT,
    FOREIGN KEY (department_id) REFERENCES departments(department_id)
);

-- Semesters 테이블
CREATE TABLE IF NOT EXISTS semesters (
    semester_id BIGSERIAL PRIMARY KEY,
    year INTEGER NOT NULL,
    season VARCHAR(10) NOT NULL,
    is_current BOOLEAN NOT NULL DEFAULT false,
    registration_start TIMESTAMP,
    registration_end TIMESTAMP
);

-- Courses 테이블
CREATE TABLE IF NOT EXISTS courses (
    course_id BIGSERIAL PRIMARY KEY,
    course_code VARCHAR(20) NOT NULL,
    title VARCHAR(200) NOT NULL,
    credits INTEGER NOT NULL,
    max_students INTEGER NOT NULL,
    department_id BIGINT,
    semester_id BIGINT,
    professor_name VARCHAR(100),
    FOREIGN KEY (department_id) REFERENCES departments(department_id),
    FOREIGN KEY (semester_id) REFERENCES semesters(semester_id)
);

-- Enrollments 테이블
CREATE TABLE IF NOT EXISTS enrollments (
    enrollment_id BIGSERIAL PRIMARY KEY,
    student_id BIGINT,
    course_id BIGINT,
    enrollment_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) DEFAULT 'ENROLLED',
    FOREIGN KEY (student_id) REFERENCES students(student_id),
    FOREIGN KEY (course_id) REFERENCES courses(course_id),
    UNIQUE(student_id, course_id)
);

-- Cart 테이블 (수강신청 장바구니)
CREATE TABLE IF NOT EXISTS carts (
    cart_id BIGSERIAL PRIMARY KEY,
    student_id BIGINT,
    course_id BIGINT,
    added_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (student_id) REFERENCES students(student_id),
    FOREIGN KEY (course_id) REFERENCES courses(course_id),
    UNIQUE(student_id, course_id)
);

-- 테이블 소유권을 univ-app으로 변경
ALTER TABLE departments OWNER TO "univ-app";
ALTER TABLE students OWNER TO "univ-app";
ALTER TABLE semesters OWNER TO "univ-app";
ALTER TABLE courses OWNER TO "univ-app";
ALTER TABLE enrollments OWNER TO "univ-app";
ALTER TABLE carts OWNER TO "univ-app";

EOF

echo "📊 샘플 데이터 생성 중..."

kubectl exec -n postgres-system postgres-cluster-1 -- psql -U postgres -d university << 'EOF'
-- 학과 데이터
INSERT INTO departments (code, name) VALUES 
('CS', '컴퓨터과학과'),
('MATH', '수학과'),
('PHYS', '물리학과'),
('ENG', '영어영문학과')
ON CONFLICT (code) DO NOTHING;

-- 학기 데이터
INSERT INTO semesters (year, season, is_current, registration_start, registration_end) VALUES 
(2024, 'SPRING', true, '2024-01-01 00:00:00', '2024-01-31 23:59:59')
ON CONFLICT DO NOTHING;

-- 강의 데이터
INSERT INTO courses (course_code, title, credits, max_students, department_id, semester_id, professor_name)
SELECT 'CS101', '프로그래밍 기초', 3, 30, d.department_id, s.semester_id, '김교수'
FROM departments d, semesters s 
WHERE d.code = 'CS' AND s.is_current = true
ON CONFLICT DO NOTHING;

INSERT INTO courses (course_code, title, credits, max_students, department_id, semester_id, professor_name)
SELECT 'CS201', '자료구조', 3, 25, d.department_id, s.semester_id, '박교수'
FROM departments d, semesters s 
WHERE d.code = 'CS' AND s.is_current = true
ON CONFLICT DO NOTHING;

INSERT INTO courses (course_code, title, credits, max_students, department_id, semester_id, professor_name)
SELECT 'MATH101', '미적분학', 3, 40, d.department_id, s.semester_id, '이교수'
FROM departments d, semesters s 
WHERE d.code = 'MATH' AND s.is_current = true
ON CONFLICT DO NOTHING;

-- 학생 데이터
INSERT INTO students (student_number, name, email, department_id)
SELECT '2024001', '홍길동', 'hong@univ.ac.kr', d.department_id
FROM departments d 
WHERE d.code = 'CS'
ON CONFLICT (student_number) DO NOTHING;

INSERT INTO students (student_number, name, email, department_id)
SELECT '2024002', '김영희', 'kim@univ.ac.kr', d.department_id
FROM departments d 
WHERE d.code = 'MATH'
ON CONFLICT (student_number) DO NOTHING;

EOF

echo "✅ University Registration 데이터베이스 초기화 완료"

# 테이블 확인
echo "📋 생성된 테이블 목록:"
kubectl exec -n postgres-system postgres-cluster-1 -- psql -U postgres -d university -c "\dt"

echo "📊 생성된 데이터 확인:"
kubectl exec -n postgres-system postgres-cluster-1 -- psql -U postgres -d university -c "
SELECT 'Departments' as table_name, count(*) as count FROM departments
UNION ALL
SELECT 'Semesters', count(*) FROM semesters  
UNION ALL
SELECT 'Courses', count(*) FROM courses
UNION ALL
SELECT 'Students', count(*) FROM students;"