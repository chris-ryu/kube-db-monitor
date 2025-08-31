#!/bin/bash

# University Registration Realistic Performance Test
# 실제 성능 한계까지 테스트하는 버전

set -e

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

print_step() {
    echo -e "${GREEN}[STEP] $1${NC}"
}

print_info() {
    echo -e "${BLUE}[INFO] $1${NC}"
}

create_realistic_jmx() {
    local jmx_file=$1
    local threads=$2
    local loops=$3
    local rampup=$4
    
    # 실제 존재하는 과목 ID들을 랜덤하게 사용
    cat > "$jmx_file" << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<jmeterTestPlan version="1.2" properties="5.0" jmeter="5.5">
  <hashTree>
    <TestPlan guiclass="TestPlanGui" testclass="TestPlan" testname="Realistic University Test" enabled="true">
      <boolProp name="TestPlan.functional_mode">false</boolProp>
      <boolProp name="TestPlan.serialize_threadgroups">false</boolProp>
    </TestPlan>
    <hashTree>
      <ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup" testname="University Students" enabled="true">
        <stringProp name="ThreadGroup.on_sample_error">continue</stringProp>
        <elementProp name="ThreadGroup.main_controller" elementType="LoopController" guiclass="LoopControllerGui" testclass="LoopController" testname="Loop Controller" enabled="true">
          <boolProp name="LoopController.continue_forever">false</boolProp>
          <stringProp name="LoopController.loops">LOOPS_PLACEHOLDER</stringProp>
        </elementProp>
        <stringProp name="ThreadGroup.num_threads">THREADS_PLACEHOLDER</stringProp>
        <stringProp name="ThreadGroup.ramp_time">RAMPUP_PLACEHOLDER</stringProp>
        <boolProp name="ThreadGroup.scheduler">false</boolProp>
      </ThreadGroup>
      <hashTree>
        <!-- 1. Search Courses -->
        <HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy" testname="🔍 Search Courses" enabled="true">
          <elementProp name="HTTPsampler.Arguments" elementType="Arguments" guiclass="HTTPArgumentsPanel" testclass="Arguments" testname="User Defined Variables" enabled="true">
            <collectionProp name="Arguments.arguments">
              <elementProp name="page" elementType="HTTPArgument">
                <boolProp name="HTTPArgument.always_encode">false</boolProp>
                <stringProp name="Argument.value">${__Random(0,5)}</stringProp>
                <stringProp name="Argument.metadata">=</stringProp>
                <boolProp name="HTTPArgument.use_equals">true</boolProp>
                <stringProp name="Argument.name">page</stringProp>
              </elementProp>
              <elementProp name="size" elementType="HTTPArgument">
                <boolProp name="HTTPArgument.always_encode">false</boolProp>
                <stringProp name="Argument.value">20</stringProp>
                <stringProp name="Argument.metadata">=</stringProp>
                <boolProp name="HTTPArgument.use_equals">true</boolProp>
                <stringProp name="Argument.name">size</stringProp>
              </elementProp>
            </collectionProp>
          </elementProp>
          <stringProp name="HTTPSampler.domain">university-registration.bitgaram.info</stringProp>
          <stringProp name="HTTPSampler.protocol">https</stringProp>
          <stringProp name="HTTPSampler.path">/api/courses</stringProp>
          <stringProp name="HTTPSampler.method">GET</stringProp>
          <boolProp name="HTTPSampler.follow_redirects">true</boolProp>
          <boolProp name="HTTPSampler.use_keepalive">true</boolProp>
          <stringProp name="HTTPSampler.connect_timeout">5000</stringProp>
          <stringProp name="HTTPSampler.response_timeout">15000</stringProp>
        </HTTPSamplerProxy>
        <hashTree/>

        <!-- Think Time -->
        <UniformRandomTimer guiclass="UniformRandomTimerGui" testclass="UniformRandomTimer" testname="Think Time 1" enabled="true">
          <stringProp name="ConstantTimer.delay">500</stringProp>
          <stringProp name="RandomTimer.range">1000</stringProp>
        </UniformRandomTimer>
        <hashTree/>

        <!-- 2. Get Available Courses -->
        <HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy" testname="📋 Get Available Courses" enabled="true">
          <stringProp name="HTTPSampler.domain">university-registration.bitgaram.info</stringProp>
          <stringProp name="HTTPSampler.protocol">https</stringProp>
          <stringProp name="HTTPSampler.path">/api/courses/available</stringProp>
          <stringProp name="HTTPSampler.method">GET</stringProp>
          <boolProp name="HTTPSampler.follow_redirects">true</boolProp>
          <boolProp name="HTTPSampler.use_keepalive">true</boolProp>
          <stringProp name="HTTPSampler.connect_timeout">5000</stringProp>
          <stringProp name="HTTPSampler.response_timeout">15000</stringProp>
        </HTTPSamplerProxy>
        <hashTree/>

        <!-- Think Time -->
        <UniformRandomTimer guiclass="UniformRandomTimerGui" testclass="UniformRandomTimer" testname="Think Time 2" enabled="true">
          <stringProp name="ConstantTimer.delay">300</stringProp>
          <stringProp name="RandomTimer.range">800</stringProp>
        </UniformRandomTimer>
        <hashTree/>

        <!-- 3. Get Course Detail (Random Course) -->
        <HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy" testname="📖 Get Course Detail" enabled="true">
          <stringProp name="HTTPSampler.domain">university-registration.bitgaram.info</stringProp>
          <stringProp name="HTTPSampler.protocol">https</stringProp>
          <stringProp name="HTTPSampler.path">/api/courses/CS00${__Random(1,9,courseNum)}</stringProp>
          <stringProp name="HTTPSampler.method">GET</stringProp>
          <boolProp name="HTTPSampler.follow_redirects">true</boolProp>
          <boolProp name="HTTPSampler.use_keepalive">true</boolProp>
          <stringProp name="HTTPSampler.connect_timeout">5000</stringProp>
          <stringProp name="HTTPSampler.response_timeout">15000</stringProp>
        </HTTPSamplerProxy>
        <hashTree/>

        <!-- Think Time -->
        <UniformRandomTimer guiclass="UniformRandomTimerGui" testclass="UniformRandomTimer" testname="Think Time 3" enabled="true">
          <stringProp name="ConstantTimer.delay">400</stringProp>
          <stringProp name="RandomTimer.range">1200</stringProp>
        </UniformRandomTimer>
        <hashTree/>

        <!-- 4. Get Cart (simulating cart check) -->
        <HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy" testname="🛒 Check My Cart" enabled="true">
          <elementProp name="HTTPsampler.Arguments" elementType="Arguments" guiclass="HTTPArgumentsPanel" testclass="Arguments" testname="User Defined Variables" enabled="true">
            <collectionProp name="Arguments.arguments">
              <elementProp name="studentId" elementType="HTTPArgument">
                <boolProp name="HTTPArgument.always_encode">false</boolProp>
                <stringProp name="Argument.value">202100${__Random(1,9)}</stringProp>
                <stringProp name="Argument.metadata">=</stringProp>
                <boolProp name="HTTPArgument.use_equals">true</boolProp>
                <stringProp name="Argument.name">studentId</stringProp>
              </elementProp>
            </collectionProp>
          </elementProp>
          <stringProp name="HTTPSampler.domain">university-registration.bitgaram.info</stringProp>
          <stringProp name="HTTPSampler.protocol">https</stringProp>
          <stringProp name="HTTPSampler.path">/api/cart</stringProp>
          <stringProp name="HTTPSampler.method">GET</stringProp>
          <boolProp name="HTTPSampler.follow_redirects">true</boolProp>
          <boolProp name="HTTPSampler.use_keepalive">true</boolProp>
          <stringProp name="HTTPSampler.connect_timeout">5000</stringProp>
          <stringProp name="HTTPSampler.response_timeout">15000</stringProp>
        </HTTPSamplerProxy>
        <hashTree/>

        <!-- Final Think Time -->
        <UniformRandomTimer guiclass="UniformRandomTimerGui" testclass="UniformRandomTimer" testname="Final Think Time" enabled="true">
          <stringProp name="ConstantTimer.delay">800</stringProp>
          <stringProp name="RandomTimer.range">1500</stringProp>
        </UniformRandomTimer>
        <hashTree/>
      </hashTree>
    </hashTree>
  </hashTree>
</jmeterTestPlan>
EOF

    # 플레이스홀더 치환
    sed -i '' "s/THREADS_PLACEHOLDER/$threads/g" "$jmx_file"
    sed -i '' "s/LOOPS_PLACEHOLDER/$loops/g" "$jmx_file"
    sed -i '' "s/RAMPUP_PLACEHOLDER/$rampup/g" "$jmx_file"
}

run_realistic_scenario() {
    local scenario=$1
    local users=$2
    local loops=$3
    local rampup=$4
    local description=$5
    
    print_step "🎯 $scenario: $description"
    print_info "👥 동시 사용자: ${users}명 | 🔄 반복: ${loops}회 | ⏱️ 램프업: ${rampup}초"
    
    local timestamp=$(date '+%Y%m%d_%H%M%S')
    local jmx_file="realistic_${scenario}.jmx"
    local result_file="results/realistic_${scenario}_${timestamp}.jtl"
    
    create_realistic_jmx "$jmx_file" "$users" "$loops" "$rampup"
    
    echo -e "${YELLOW}▶️  테스트 시작...${NC}"
    local start_time=$(date +%s)
    
    jmeter -n -t "$jmx_file" -l "$result_file" -q /dev/null >/dev/null 2>&1
    
    local end_time=$(date +%s)
    local duration=$((end_time - start_time))
    
    rm -f "$jmx_file"
    
    if [ $? -eq 0 ]; then
        # 결과 분석
        local total_requests=$(tail -n +2 "$result_file" | wc -l | tr -d ' ')
        local error_count=$(tail -n +2 "$result_file" | awk -F',' '$8=="false" {count++} END {print count+0}')
        local success_rate=$(echo "scale=1; ($total_requests - $error_count) * 100 / $total_requests" | bc -l 2>/dev/null || echo "100.0")
        local avg_response_time=$(tail -n +2 "$result_file" | awk -F',' '{sum+=$2; count++} END {if(count>0) print int(sum/count); else print 0}')
        local max_response_time=$(tail -n +2 "$result_file" | awk -F',' '{if($2>max) max=$2} END {print int(max)}')
        local tps=$(echo "scale=1; $total_requests / $duration" | bc -l 2>/dev/null || echo "0")
        
        echo ""
        echo -e "${GREEN}✅ $scenario 완료 (${duration}초 소요)${NC}"
        echo "  📊 총 요청: $total_requests"
        echo "  ✅ 성공: $((total_requests - error_count)) (${success_rate}%)"
        echo "  ❌ 실패: $error_count"
        echo "  ⏱️  평균 응답시간: ${avg_response_time}ms"
        echo "  📈 최대 응답시간: ${max_response_time}ms"
        echo "  🚀 처리량: ${tps} TPS"
        echo "  📁 결과파일: $result_file"
        
    else
        echo -e "${RED}❌ $scenario 실패${NC}"
        return 1
    fi
    
    echo ""
}

main() {
    echo -e "${BLUE}"
    echo "╔════════════════════════════════════════════════════╗"
    echo "║           🎓 Realistic Performance Test             ║"
    echo "║                시스템 한계 테스트                      ║"
    echo "╚════════════════════════════════════════════════════╝"
    echo -e "${NC}"
    echo ""
    
    mkdir -p results
    
    print_info "🌐 KubeDB Monitor: https://kube-db-mon-dashboard.bitgaram.info"
    echo ""
    
    # Phase 1: Light Load
    run_realistic_scenario "light" 10 5 10 "가벼운 부하 - 정상 동작 확인"
    sleep 3
    
    # Phase 2: Medium Load  
    run_realistic_scenario "medium" 30 5 15 "중간 부하 - 일반적 사용량"
    sleep 3
    
    # Phase 3: Heavy Load
    run_realistic_scenario "heavy" 80 3 20 "높은 부하 - 피크 타임"
    sleep 3
    
    # Phase 4: Extreme Load
    run_realistic_scenario "extreme" 150 2 30 "극한 부하 - 시스템 한계 테스트"
    
    echo -e "${GREEN}🎉 모든 테스트 완료!${NC}"
    echo -e "${BLUE}📊 KubeDB Monitor에서 성능 분석 결과를 확인하세요!${NC}"
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main
fi