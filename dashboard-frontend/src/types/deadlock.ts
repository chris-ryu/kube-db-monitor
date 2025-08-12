/**
 * 데드락 이벤트 타입 정의
 */
export interface DeadlockEvent {
  id: string
  participants: string[] // 트랜잭션 ID 목록
  detectionTime: string
  recommendedVictim: string
  lockChain: string[] // 락 체인 시각화용
  severity: 'critical' | 'warning' | 'info'
  status: 'active' | 'resolved' | 'ignored'
  pod_name?: string
  namespace?: string
  
  // 추가 메타데이터
  cycleLength?: number
  victimCost?: number
  resolvedAt?: string
  resolvedBy?: 'auto' | 'manual'
}

/**
 * 데드락 통계
 */
export interface DeadlockStats {
  totalDetected: number
  activeCount: number
  resolvedCount: number
  averageResolutionTime: number // ms
  mostFrequentTables: string[]
  peakHours: number[] // 0-23시간
}

/**
 * 락 정보
 */
export interface LockInfo {
  transactionId: string
  resourceId: string
  lockType: 'shared' | 'exclusive'
  waitTime: number // ms
  holdTime?: number // ms
}