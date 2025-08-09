/**
 * KubeDB Monitor 메트릭 타입 정의
 * Java Agent에서 전송되는 JSON 구조와 일치
 */

export interface QueryMetrics {
  timestamp: string
  pod_name?: string
  namespace?: string
  event_type: EventType
  data?: QueryData
  context?: ExecutionContext
  metrics?: SystemMetrics
}

export interface QueryData {
  query_id: string
  sql_hash?: string
  sql_pattern?: string
  sql_type?: SqlType
  table_names?: string[]
  execution_time_ms?: number
  rows_affected?: number
  connection_id?: string
  thread_name?: string
  memory_used_bytes?: number
  cpu_time_ms?: number
  io_read_bytes?: number
  io_write_bytes?: number
  lock_time_ms?: number
  status: ExecutionStatus
  error_code?: string
  error_message?: string
  explain_plan?: Record<string, any>
  stack_trace?: string[]
  complexity_score?: number
  index_usage?: IndexUsageInfo
  cache_hit_ratio?: number
}

export interface ExecutionContext {
  request_id?: string
  user_session?: string
  api_endpoint?: string
  business_operation?: string
  user_id?: string
  client_ip?: string
  user_agent?: string
  trace_id?: string
  span_id?: string
  parent_span_id?: string
}

export interface SystemMetrics {
  // 커넥션 풀 정보
  connection_pool_active?: number
  connection_pool_idle?: number
  connection_pool_max?: number
  connection_pool_usage_ratio?: number
  
  // 메모리 정보
  heap_used_mb?: number
  heap_max_mb?: number
  heap_usage_ratio?: number
  non_heap_used_mb?: number
  
  // GC 정보
  gc_count?: number
  gc_time_ms?: number
  gc_frequency?: number
  
  // CPU 정보
  cpu_usage_ratio?: number
  process_cpu_time_ms?: number
  
  // 스레드 정보
  thread_count?: number
  peak_thread_count?: number
  
  // 클래스 로딩 정보
  loaded_class_count?: number
  unloaded_class_count?: number
}

export interface IndexUsageInfo {
  indexes_used?: string[]
  full_table_scan?: boolean
  index_efficiency_score?: number
  missing_index_suggestions?: string[]
}

export type SqlType = 
  | 'SELECT' 
  | 'INSERT' 
  | 'UPDATE' 
  | 'DELETE' 
  | 'CREATE' 
  | 'DROP' 
  | 'ALTER' 
  | 'TRUNCATE' 
  | 'UNKNOWN'

export type ExecutionStatus = 'SUCCESS' | 'ERROR' | 'TIMEOUT' | 'CANCELLED'

export type EventType = 
  | 'query_execution'
  | 'query_start' 
  | 'query_complete'
  | 'slow_query'
  | 'query_error'
  | 'connection_pool_status'
  | 'system_metrics'
  | 'user_session'
  | 'dashboard_access'

// WebSocket 메시지 타입
export interface WebSocketMessage {
  type: string
  data: QueryMetrics
  timestamp: string
}

// 대시보드에서 사용할 집계된 메트릭 타입들
export interface AggregatedMetrics {
  qps: number // Queries Per Second
  avg_latency: number // Average latency in ms
  error_rate: number // Error rate as percentage
  active_connections: number
  max_connections: number
  heap_usage_ratio: number
  cpu_usage_ratio: number
}

export interface QueryTypeStats {
  type: SqlType
  count: number
  avg_execution_time: number
  error_count: number
  percentage: number
}

export interface TableAccessStats {
  table_name: string
  access_count: number
  avg_execution_time: number
  percentage: number
}

export interface ExecutionTimeDistribution {
  fast: number // < 10ms
  medium: number // 10-50ms  
  slow: number // > 50ms
}

// 시계열 데이터 포인트
export interface TimeSeriesDataPoint {
  timestamp: string
  value: number
  label?: string
}

// 쿼리 히트맵 데이터
export interface HeatmapDataPoint {
  time: string
  intensity: number
  avg_latency: number
  query_count: number
}

// 알림 타입
export interface Alert {
  id: string
  type: 'warning' | 'error' | 'info'
  title: string
  message: string
  timestamp: string
  acknowledged: boolean
  query_id?: string
}

// 대시보드 설정 타입
export interface DashboardConfig {
  refresh_interval: number // seconds
  slow_query_threshold: number // milliseconds
  max_displayed_queries: number
  enable_animations: boolean
  theme: 'dark' | 'light'
}