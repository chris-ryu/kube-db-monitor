package com.university.registration.controller;

import com.university.registration.config.UniversalDataInitializer;
import com.university.registration.service.DataResetService;
// Swagger 의존성 없이 간단한 주석으로 대체
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 데모 환경용 컨트롤러
 * 
 * 주요 기능:
 * - 데이터 리셋 (demo.features.data-reset-api=true 일때만 활성화)
 * - 환경 상태 조회
 * - 데모 시나리오별 데이터 생성
 */
@RestController
@RequestMapping("/demo")
// 데모 환경 관리 API
@ConditionalOnProperty(name = "demo.features.data-reset-api", havingValue = "true")
public class DemoController {

    private static final Logger logger = LoggerFactory.getLogger(DemoController.class);

    @Autowired
    private DataResetService dataResetService;

    @Autowired
    private UniversalDataInitializer dataInitializer;

    // 데모 데이터 완전 리셋: 모든 데이터를 삭제하고 데모용 데이터를 새로 생성합니다
    @PostMapping("/reset")
    public ResponseEntity<?> resetDemoData(@RequestParam(defaultValue = "demo") String mode) {
        logger.warn("🔄 데모 데이터 리셋 요청 - Mode: {}", mode);
        
        try {
            // 데이터 리셋 실행
            dataResetService.resetAllData(mode);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "데모 데이터가 성공적으로 리셋되었습니다.",
                "mode", mode,
                "timestamp", System.currentTimeMillis()
            ));
            
        } catch (Exception e) {
            logger.error("데모 데이터 리셋 실패", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "데이터 리셋 중 오류가 발생했습니다: " + e.getMessage(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }

    // 데모 환경 상태 조회
    @GetMapping("/status")
    public ResponseEntity<?> getDemoStatus() {
        return ResponseEntity.ok(dataResetService.getDataStatus());
    }

    // 특정 시나리오 데이터 생성
    @PostMapping("/scenario/{scenarioName}")
    public ResponseEntity<?> createScenarioData(@PathVariable String scenarioName) {
        logger.info("📝 시나리오 데이터 생성 요청: {}", scenarioName);
        
        try {
            dataResetService.createScenarioData(scenarioName);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "시나리오 데이터가 생성되었습니다: " + scenarioName,
                "scenario", scenarioName,
                "timestamp", System.currentTimeMillis()
            ));
            
        } catch (Exception e) {
            logger.error("시나리오 데이터 생성 실패: {}", scenarioName, e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "시나리오 데이터 생성 실패: " + e.getMessage(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }

    // 지원하는 시나리오 목록 조회
    @GetMapping("/scenarios")
    public ResponseEntity<?> getAvailableScenarios() {
        return ResponseEntity.ok(Map.of(
            "scenarios", Map.of(
                "deadlock", "데드락 시뮬레이션용 데이터",
                "heavy-load", "고부하 테스트용 데이터", 
                "edge-case", "엣지 케이스 테스트 데이터",
                "clean", "기본 깨끗한 데이터"
            ),
            "modes", Map.of(
                "test", "최소 테스트 데이터",
                "demo", "데모용 현실적 데이터",
                "dev", "개발용 다양한 데이터",
                "basic", "기본 데이터"
            )
        ));
    }
}