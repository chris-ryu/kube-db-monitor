/**
 * 트랜잭션 관련 타입 정의
 */

export interface TransactionEvent {
  id: string
  transaction_id: string
  start_time: string
  end_time?: string
  status: 'active' | 'committed' | 'rolled_back' | 'timeout'
  duration_ms?: number
  query_count: number
  total_execution_time_ms: number
  
  // 메타데이터
  pod_name?: string
  namespace?: string
  isolation_level?: string
  is_read_only?: boolean
  
  // 쿼리 목록
  queries: TransactionQuery[]
  
  // 성능 메트릭
  cpu_time_ms?: number
  memory_used_bytes?: number
  io_operations?: number
}

export interface TransactionQuery {
  query_id: string
  sql_pattern: string
  sql_type: 'SELECT' | 'INSERT' | 'UPDATE' | 'DELETE' | 'OTHER'
  execution_time_ms: number
  timestamp: string
  sequence_number: number
  table_names?: string[]
  rows_affected?: number
  status: 'success' | 'error'
  error_message?: string
}

export interface TransactionTimeline {
  transactions: TransactionEvent[]
  timeRange: {
    start: string
    end: string
  }
  statistics: {
    total_transactions: number
    active_count: number
    committed_count: number
    rolled_back_count: number
    avg_duration_ms: number
    slowest_transaction?: TransactionEvent
  }
}

export interface TransactionFilter {
  status?: string[]
  pod_name?: string
  namespace?: string
  min_duration_ms?: number
  max_duration_ms?: number
  min_query_count?: number
  sql_type?: string[]
  time_range?: {
    start: string
    end: string
  }
}

/**
 * 트랜잭션 성능 등급
 */
export type TransactionPerformance = 'fast' | 'normal' | 'slow' | 'critical'

export function getTransactionPerformance(durationMs: number, queryCount: number): TransactionPerformance {
  if (durationMs < 100) return 'fast'
  if (durationMs < 1000) return 'normal'  
  if (durationMs < 5000) return 'slow'
  return 'critical'
}