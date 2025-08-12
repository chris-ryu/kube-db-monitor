#!/bin/bash

# University Registration API Test Script
# 수강신청 시스템 API 동작 확인 및 TPS/Long Running Transaction 생성 테스트

BASE_URL="https://university-registration.bitgaram.info"
DASHBOARD_URL="https://kube-db-mon-dashboard.bitgaram.info"

echo "🧪 University Registration API Test Suite"
echo "========================================"

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 함수 정의
print_status() {
    local status=$1
    local message=$2
    case $status in
        "SUCCESS")
            echo -e "${GREEN}[✓ SUCCESS]${NC} $message"
            ;;
        "ERROR")
            echo -e "${RED}[✗ ERROR]${NC} $message"
            ;;
        "INFO")
            echo -e "${BLUE}[ℹ INFO]${NC} $message"
            ;;
        "WARNING")
            echo -e "${YELLOW}[⚠ WARNING]${NC} $message"
            ;;
    esac
}

test_api_endpoint() {
    local endpoint=$1
    local expected_status=$2
    local description=$3
    
    print_status "INFO" "Testing: $description"
    print_status "INFO" "Endpoint: $BASE_URL$endpoint"
    
    response=$(curl -s -w "%{http_code}" -o /tmp/api_response.json "$BASE_URL$endpoint")
    http_status="${response: -3}"
    
    if [ "$http_status" -eq "$expected_status" ]; then
        print_status "SUCCESS" "HTTP $http_status - $description"
        if [ -s /tmp/api_response.json ]; then
            echo "Response preview:"
            head -c 200 /tmp/api_response.json
            echo ""
        fi
        return 0
    else
        print_status "ERROR" "Expected HTTP $expected_status, got HTTP $http_status - $description"
        if [ -s /tmp/api_response.json ]; then
            echo "Error response:"
            cat /tmp/api_response.json
            echo ""
        fi
        return 1
    fi
}

generate_load() {
    local endpoint=$1
    local requests=$2
    local description=$3
    
    print_status "INFO" "🔄 Generating load: $description"
    print_status "INFO" "Making $requests requests to $endpoint"
    
    for i in $(seq 1 $requests); do
        curl -s "$BASE_URL$endpoint" > /dev/null &
        if [ $((i % 10)) -eq 0 ]; then
            echo -n "."
        fi
    done
    wait
    echo ""
    print_status "SUCCESS" "Load generation completed: $requests requests"
}

echo ""
print_status "INFO" "Starting API functionality tests..."

# Test 1: Health Check
echo ""
echo "Test 1: Health Check"
echo "-------------------"
test_api_endpoint "/api/actuator/health" 200 "Application health check"

# Test 2: Course List
echo ""
echo "Test 2: Course List"
echo "------------------"
test_api_endpoint "/api/courses" 200 "Get all courses"

# Test 3: Students List
echo ""
echo "Test 3: Students List"
echo "-------------------"
test_api_endpoint "/api/students" 200 "Get all students"

# Test 4: Enrollments List
echo ""
echo "Test 4: Enrollments List"
echo "----------------------"
test_api_endpoint "/api/enrollments" 200 "Get all enrollments"

# Test 5: Course Detail
echo ""
echo "Test 5: Course Detail"
echo "-------------------"
test_api_endpoint "/api/courses/1" 200 "Get specific course detail"

echo ""
print_status "INFO" "🎯 Starting TPS (Transactions Per Second) generation..."

# Generate TPS events (high frequency queries)
echo ""
echo "TPS Test 1: High Frequency Course Queries"
echo "========================================="
generate_load "/api/courses" 50 "High frequency course list queries (50 requests)"

echo ""
echo "TPS Test 2: Mixed API Calls"
echo "==========================="
print_status "INFO" "Generating mixed API load pattern..."

# Generate concurrent requests to different endpoints
generate_load "/api/students" 20 "Student queries" &
generate_load "/api/enrollments" 20 "Enrollment queries" &
generate_load "/api/courses/1" 15 "Course detail queries" &
generate_load "/api/courses/2" 15 "Course detail queries" &
wait

print_status "SUCCESS" "Mixed load pattern completed"

echo ""
print_status "INFO" "🐌 Starting Long Running Transaction simulation..."

# Test Long Running Transactions (complex queries that might take time)
echo ""
echo "Long Running Transaction Test"
echo "==========================="

print_status "INFO" "Simulating potentially long-running operations..."

# Generate concurrent complex queries that might cause long transactions
for i in {1..10}; do
    print_status "INFO" "Starting concurrent operation batch $i"
    
    # Multiple concurrent requests to same course (potential for locking)
    curl -s "$BASE_URL/api/courses/1" > /dev/null &
    curl -s "$BASE_URL/api/enrollments" > /dev/null &
    curl -s "$BASE_URL/api/students" > /dev/null &
    
    # Small delay between batches
    sleep 0.1
done
wait

print_status "SUCCESS" "Long running transaction simulation completed"

echo ""
echo "📊 Monitoring Instructions"
echo "========================="
print_status "INFO" "Check the dashboard for generated metrics:"
print_status "INFO" "Dashboard URL: $DASHBOARD_URL"
echo ""
echo "Expected results on dashboard:"
echo "✓ TPS (Transactions Per Second) > 10"
echo "✓ Long Running Transaction alerts (threshold: 5s)"
echo "✓ Recent Queries showing API calls"
echo "✓ Transaction Timeline with performance indicators"
echo ""

# Final API status check
echo ""
echo "Final Status Check"
echo "=================="
print_status "INFO" "Verifying API is still responsive..."
test_api_endpoint "/api/actuator/health" 200 "Final health check"

echo ""
print_status "SUCCESS" "🎉 University Registration API test completed!"
print_status "INFO" "Check dashboard at: $DASHBOARD_URL"

# Cleanup
rm -f /tmp/api_response.json

echo ""
print_status "INFO" "💡 To continuously generate metrics, run this script periodically:"
print_status "INFO" "    watch -n 30 ./test-university-api.sh"