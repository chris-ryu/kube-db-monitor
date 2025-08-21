-- PostgreSQL 테스트용 기본 데이터 (JPA 엔티티 호환)
-- KubeDB Monitor Agent PostgreSQL 호환성 테스트를 위한 데이터셋

-- 테이블 생성 (JPA 엔티티와 일치)
CREATE TABLE IF NOT EXISTS departments (
    department_id BIGINT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(10),
    created_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS semesters (
    semester_id BIGINT PRIMARY KEY,
    year INTEGER NOT NULL,
    season VARCHAR(10) NOT NULL,
    registration_start TIMESTAMP NOT NULL,
    registration_end TIMESTAMP NOT NULL,
    is_current BOOLEAN DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS students (
    student_id VARCHAR(20) PRIMARY KEY,
    student_name VARCHAR(100) NOT NULL,
    email VARCHAR(100),
    department_id BIGINT,
    grade INTEGER,
    total_credits INTEGER DEFAULT 0,
    max_credits INTEGER DEFAULT 21,
    password VARCHAR(255),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    FOREIGN KEY (department_id) REFERENCES departments(department_id)
);

CREATE TABLE IF NOT EXISTS courses (
    course_id VARCHAR(20) PRIMARY KEY,
    course_name VARCHAR(200) NOT NULL,
    professor VARCHAR(100),
    credits INTEGER,
    capacity INTEGER,
    enrolled_count INTEGER DEFAULT 0,
    department_id BIGINT,
    semester_id BIGINT,
    classroom VARCHAR(50),
    day_time VARCHAR(100),
    is_active BOOLEAN DEFAULT TRUE,
    popularity_level VARCHAR(20),
    prerequisite_course_id VARCHAR(20),
    created_at TIMESTAMP,
    FOREIGN KEY (department_id) REFERENCES departments(department_id),
    FOREIGN KEY (semester_id) REFERENCES semesters(semester_id),
    FOREIGN KEY (prerequisite_course_id) REFERENCES courses(course_id)
);

CREATE TABLE IF NOT EXISTS enrollments (
    enrollment_id BIGINT PRIMARY KEY,
    student_id VARCHAR(20),
    course_id VARCHAR(20),
    semester_id BIGINT,
    status VARCHAR(20) DEFAULT 'ENROLLED',
    enrolled_at TIMESTAMP,
    FOREIGN KEY (student_id) REFERENCES students(student_id),
    FOREIGN KEY (course_id) REFERENCES courses(course_id),
    FOREIGN KEY (semester_id) REFERENCES semesters(semester_id)
);

CREATE TABLE IF NOT EXISTS cart (
    cart_id BIGINT PRIMARY KEY,
    student_id VARCHAR(20),
    course_id VARCHAR(20),
    added_at TIMESTAMP,
    FOREIGN KEY (student_id) REFERENCES students(student_id),
    FOREIGN KEY (course_id) REFERENCES courses(course_id)
);

-- 학과 데이터 (JPA 엔티티와 일치)
INSERT INTO departments (department_id, name, code, created_at) VALUES 
(1, '컴퓨터과학과', '공과대학', NOW()),
(2, '전자공학과', '공과대학', NOW()),
(3, '수학과', '자연과학대학', NOW());

-- 학기 데이터 (JPA 엔티티와 일치)
INSERT INTO semesters (semester_id, year, season, registration_start, registration_end, is_current) VALUES 
(1, 2024, 'SPRING', CURRENT_TIMESTAMP - INTERVAL '30 days', CURRENT_TIMESTAMP + INTERVAL '30 days', true),
(2, 2023, 'FALL', CURRENT_TIMESTAMP - INTERVAL '150 days', CURRENT_TIMESTAMP - INTERVAL '120 days', false);

-- 학생 데이터  
INSERT INTO students (student_id, student_name, email, department_id, grade, total_credits, max_credits, password, created_at, updated_at) VALUES
('2020001', '김테스트', 'test1@university.edu', 1, 3, 45, 21, 'password123', NOW(), NOW()),
('2020002', '이호환', 'test2@university.edu', 1, 2, 30, 21, 'password123', NOW(), NOW()),
('2020003', '박검증', 'test3@university.edu', 2, 4, 60, 21, 'password123', NOW(), NOW()),
('2024001', '신규학생', 'new@university.edu', 1, 1, 0, 21, 'password123', NOW(), NOW()),
('TEST001', '테스트학생', 'test@university.edu', 1, 3, 45, 21, 'demo123', NOW(), NOW());

-- 과목 데이터 (PostgreSQL 타입 캐스팅 테스트를 위한 다양한 문자열)
INSERT INTO courses (course_id, course_name, professor, credits, capacity, enrolled_count, 
                    department_id, semester_id, classroom, day_time, is_active, 
                    popularity_level, created_at) VALUES
-- 기본 ASCII 문자
('CS101', 'Database Systems', 'Prof. Kim', 3, 30, 25, 1, 1, 'E101', 'MON_1', true, 'HIGH', NOW()),
('CS102', 'Data Structures', 'Prof. Lee', 3, 35, 20, 1, 1, 'E102', 'TUE_2', true, 'MEDIUM', NOW()),

-- 한글 문자 (bytea 타입 캐스팅 문제 테스트)
('CS201', '데이터베이스설계', '김교수', 3, 25, 23, 1, 1, 'E201', 'WED_1', true, 'HIGH', NOW()),
('EE101', '전자회로', '이교수', 3, 40, 15, 2, 1, 'F101', 'THU_1', true, 'LOW', NOW()),

-- 특수문자 포함 (LOWER 함수 테스트)
('MATH101', 'Calculus I & II', 'Prof. Park-Smith', 4, 50, 45, 3, 1, 'M101', 'FRI_2', true, 'HIGH', NOW()),
('CS301', 'AI/ML Introduction', 'Dr. O''Connor', 3, 20, 18, 1, 1, 'E301', 'MON_3', true, 'MEDIUM', NOW()),

-- 추가 테스트용 과목들 (데이터베이스 키워드 검색용)
('CS103', 'Database Design', 'Prof. Database', 3, 30, 15, 1, 1, 'E103', 'WED_2', true, 'HIGH', NOW()),
('CS104', 'Advanced Database', 'Dr. Kim', 3, 25, 10, 1, 1, 'E104', 'FRI_1', true, 'MEDIUM', NOW());

-- 수강신청 데이터
INSERT INTO enrollments (enrollment_id, student_id, course_id, semester_id, status, enrolled_at) VALUES
(1, '2020001', 'CS101', 1, 'ENROLLED', NOW()),
(2, '2020001', 'CS102', 1, 'ENROLLED', NOW()),
(3, '2020002', 'CS101', 1, 'ENROLLED', NOW()),
(4, '2020003', 'EE101', 1, 'ENROLLED', NOW()),
(5, 'TEST001', 'CS201', 1, 'ENROLLED', NOW());

-- 장바구니 데이터
INSERT INTO cart (cart_id, student_id, course_id, added_at) VALUES
(1, '2020001', 'CS201', NOW()),
(2, '2020002', 'MATH101', NOW()),
(3, '2020003', 'CS301', NOW()),
(4, 'TEST001', 'CS301', NOW());