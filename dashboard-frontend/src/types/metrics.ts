/**
 * KubeDB Monitor 메트릭 타입 정의
 * Java Agent에서 전송되는 JSON 구조와 일치
 */

import { QueryHistoryInfo } from './transaction'

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
  // TPS and Long Running Transaction specific fields
  tps_value?: number
  transaction_duration?: number
  transaction_id?: string
  
  // Long Running Transaction SQL query information
  current_query?: string
  stored_procedure?: string
  query_history?: QueryHistoryInfo[]

  // Transaction event specific fields
  transaction_type?: 'COMMIT' | 'ROLLBACK' | 'BEGIN'
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
  // 기본 커넥션 풀 정보
  connection_pool_active?: number
  connection_pool_idle?: number
  connection_pool_max?: number
  connection_pool_usage_ratio?: number
  
  // 고급 커넥션 풀 메트릭
  connection_pool_peak_active?: number
  connection_pool_peak_timestamp?: number
  connection_pool_requests_per_second?: number
  connection_pool_health_score?: number
  connection_pool_average_hold_time?: number
  connection_pool_waiting_threads?: number
  
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

export type ExecutionStatus = 'SUCCESS' | 'ERROR' | 'TIMEOUT' | 'CANCELLED' | 'completed' | 'error'

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
  | 'tps_event'
  | 'long_running_transaction'
  | 'transaction_event'
  | 'deadlock_event'
  | 'deadlock_detected'

// WebSocket 메시지 타입
export interface WebSocketMessage {
  type: string
  data: QueryMetrics
  timestamp: string
}

// 대시보드에서 사용할 집계된 메트릭 타입들
export interface AggregatedMetrics {
  qps: number // Queries Per Second
  tps?: number // Transactions Per Second
  avg_latency: number // Average latency in ms
  avgLatency?: number // camelCase version for consistency
  error_rate: number // Error rate as percentage
  transactionCount?: number // Total transaction count in time window
  
  // 기본 Connection Pool 메트릭
  active_connections: number
  activeConnections?: number // camelCase version
  idle_connections: number
  idleConnections?: number // camelCase version
  max_connections: number
  maxConnections?: number // camelCase version
  pool_usage_ratio: number
  poolUsageRatio?: number // camelCase version
  
  // 고급 Connection Pool 메트릭
  peak_active_connections?: number
  peakActiveConnections?: number // camelCase version
  pool_health_score?: number
  poolHealthScore?: number // camelCase version
  connection_requests_per_second?: number
  connectionRequestsPerSecond?: number // camelCase version
  average_hold_time?: number
  averageHoldTime?: number // camelCase version
  waiting_threads?: number
  waitingThreads?: number // camelCase version
  peak_timestamp?: number
  peakTimestamp?: number // camelCase version
  
  // 시스템 메트릭
  heap_usage_ratio: number
  cpu_usage_ratio: number
  pool_type?: string
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

// Connection Pool 상세 정보 타입
export interface ConnectionPoolDetail {
  pool_name: string
  pool_type: 'HikariCP' | 'Tomcat' | 'C3P0' | 'DBCP' | 'Unknown'
  current_active: number
  current_idle: number
  max_pool_size: number
  peak_active: number
  peak_timestamp: string
  health_score: number // 0-100
  requests_per_second: number
  average_hold_time: number // milliseconds
  waiting_threads: number
  usage_ratio: number // 0-1
  total_connections_created?: number
  total_connections_closed?: number
}

// Connection Pool Health Status 타입
export type PoolHealthStatus = 'excellent' | 'good' | 'warning' | 'critical'

export interface PoolHealthInfo {
  status: PoolHealthStatus
  score: number
  issues: string[]
  recommendations: string[]
}

// Connection Pool 사용 패턴 타입
export interface PoolUsagePattern {
  timestamp: string
  active_connections: number
  requests_per_second: number
  response_time_ms: number
}

// Live Performance Chart Data
export interface PerformanceDataPoint {
  timestamp: number
  connectionPool: number  // percentage (0-100)
  tps: number            // transactions per second
  latency: number        // milliseconds
}

export interface ChartMetrics {
  dataPoints: PerformanceDataPoint[]
  maxDataPoints: number
  timeRange: number      // in seconds
}

// 대시보드 설정 타입
export interface DashboardConfig {
  refresh_interval: number // seconds
  slow_query_threshold: number // milliseconds
  max_displayed_queries: number
  enable_animations: boolean
  theme: 'dark' | 'light'
}