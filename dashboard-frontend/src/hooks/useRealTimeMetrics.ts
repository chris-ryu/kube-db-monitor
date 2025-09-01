import { useState, useEffect, useCallback, useRef } from 'react'
import { QueryMetrics, WebSocketMessage, AggregatedMetrics, EventType } from '@/types/metrics'

export interface UseRealTimeMetricsOptions {
  wsUrl?: string
  maxMetrics?: number
  eventTypes?: EventType[]
  autoReconnect?: boolean
  reconnectInterval?: number
}

export interface UseRealTimeMetricsReturn {
  metrics: QueryMetrics[]
  isConnected: boolean
  connectionStatus: 'connecting' | 'connected' | 'disconnected' | 'error'
  aggregatedMetrics: AggregatedMetrics
  reconnect: () => void
  clearMetrics: () => void
}

const DEFAULT_OPTIONS: Required<UseRealTimeMetricsOptions> = {
  wsUrl: 'ws://localhost:8080/ws',
  maxMetrics: 1000,
  eventTypes: [], // Empty array means accept all types
  autoReconnect: true,
  reconnectInterval: 5000,
}

export function useRealTimeMetrics(
  options: UseRealTimeMetricsOptions = {}
): UseRealTimeMetricsReturn {
  const config = { ...DEFAULT_OPTIONS, ...options }
  
  const [metrics, setMetrics] = useState<QueryMetrics[]>([])
  const [connectionStatus, setConnectionStatus] = useState<'connecting' | 'connected' | 'disconnected' | 'error'>('connecting')
  
  const wsRef = useRef<WebSocket | null>(null)
  const reconnectTimeoutRef = useRef<NodeJS.Timeout | null>(null)
  const mountedRef = useRef(true)

  const isConnected = connectionStatus === 'connected'

  // Calculate aggregated metrics from current metrics
  const aggregatedMetrics: AggregatedMetrics = calculateAggregatedMetrics(metrics)

  const connect = useCallback(() => {
    if (!mountedRef.current) return

    try {
      setConnectionStatus('connecting')
      
      const ws = new WebSocket(config.wsUrl)
      wsRef.current = ws

      ws.addEventListener('open', () => {
        if (!mountedRef.current) return
        setConnectionStatus('connected')
      })

      ws.addEventListener('message', (event) => {
        if (!mountedRef.current) return
        
        try {
          const message: WebSocketMessage = JSON.parse(event.data)
          console.log('📥 WebSocket message received:', message)
          
          // Filter by event types if specified
          if (config.eventTypes.length > 0 && !config.eventTypes.includes(message.data.event_type)) {
            console.log('🚫 Message filtered out by event type:', message.data.event_type)
            return
          }
          
          setMetrics(prev => {
            const newMetrics = [message.data, ...prev]
            console.log('📊 Updated metrics count:', newMetrics.length)
            return newMetrics.slice(0, config.maxMetrics)
          })
        } catch (error) {
          console.warn('Failed to parse WebSocket message:', error, event.data)
        }
      })

      ws.addEventListener('close', () => {
        if (!mountedRef.current) return
        
        setConnectionStatus('disconnected')
        
        if (config.autoReconnect) {
          reconnectTimeoutRef.current = setTimeout(() => {
            if (mountedRef.current) {
              connect()
            }
          }, config.reconnectInterval)
        }
      })

      ws.addEventListener('error', (error) => {
        if (!mountedRef.current) return
        
        console.error('WebSocket error:', error)
        setConnectionStatus('error')
      })

    } catch (error) {
      if (!mountedRef.current) return
      
      console.error('Failed to create WebSocket connection:', error)
      setConnectionStatus('error')
    }
  }, [config.wsUrl, config.eventTypes, config.maxMetrics, config.autoReconnect, config.reconnectInterval])

  const reconnect = useCallback(() => {
    if (wsRef.current) {
      wsRef.current.close()
    }
    if (reconnectTimeoutRef.current) {
      clearTimeout(reconnectTimeoutRef.current)
    }
    connect()
  }, [connect])

  const clearMetrics = useCallback(() => {
    setMetrics([])
  }, [])

  useEffect(() => {
    mountedRef.current = true
    connect()

    // Mock data event listener for testing
    const handleMockMessage = (event: any) => {
      if (!mountedRef.current) return
      
      console.log('🎭 Mock WebSocket message received:', event.detail)
      
      setMetrics(prev => {
        const newMetrics = [event.detail.data, ...prev]
        console.log('📊 Mock updated metrics count:', newMetrics.length)
        return newMetrics.slice(0, config.maxMetrics)
      })
    }
    
    window.addEventListener('mockWebSocketMessage', handleMockMessage)

    return () => {
      mountedRef.current = false
      
      if (wsRef.current) {
        wsRef.current.close()
      }
      
      if (reconnectTimeoutRef.current) {
        clearTimeout(reconnectTimeoutRef.current)
      }
      
      window.removeEventListener('mockWebSocketMessage', handleMockMessage)
    }
  }, [connect, config.maxMetrics])

  return {
    metrics,
    isConnected,
    connectionStatus,
    aggregatedMetrics,
    reconnect,
    clearMetrics,
  }
}

function calculateAggregatedMetrics(metrics: QueryMetrics[]): AggregatedMetrics {
  console.log('🔄 Calculating aggregated metrics from', metrics.length, 'total metrics')
  
  if (metrics.length === 0) {
    return {
      qps: 0,
      avg_latency: 0,
      error_rate: 0,
      active_connections: 0,
      idle_connections: 0,
      max_connections: 0,
      pool_usage_ratio: 0,
      heap_usage_ratio: 0,
      cpu_usage_ratio: 0,
    }
  }

  // Get recent metrics (last 60 seconds)
  const now = new Date()
  const oneMinuteAgo = new Date(now.getTime() - 60 * 1000)
  const recentMetrics = metrics.filter(metric => {
    const metricTime = new Date(metric.timestamp)
    return metricTime >= oneMinuteAgo
  })

  console.log('📊 Recent metrics (last 60s):', recentMetrics.length, 'filtered from', metrics.length)

  // ONLY use query_execution events for latency calculation
  const queryMetrics = recentMetrics.filter(m => 
    m.data && 
    m.event_type === 'query_execution' && 
    m.data.execution_time_ms !== undefined &&
    m.data.execution_time_ms > 0 &&
    m.data.execution_time_ms < 60000  // Exclude unrealistic values (>60s)
  )
  const transactionMetrics = recentMetrics.filter(m => m.data && ['tps_event', 'long_running_transaction'].includes(m.event_type))
  
  console.log('🔍 Query execution metrics:', queryMetrics.length)
  console.log('📈 Transaction metrics:', transactionMetrics.length)
  
  // Calculate QPS (queries per second) - use actual time window
  const timeWindowSeconds = Math.min(60, (now.getTime() - oneMinuteAgo.getTime()) / 1000)
  const qps = queryMetrics.length / Math.max(timeWindowSeconds, 1)

  // Calculate average latency - only from realistic execution times for SQL queries
  const executionTimes = queryMetrics
    .map(m => m.data?.execution_time_ms)
    .filter((time): time is number => 
      time !== undefined && 
      time > 0 && 
      time < 10000  // Only accept times under 10 seconds (realistic for SQL queries)
    )
  
  console.log('⏱️ Valid execution times:', executionTimes.length, 'values:', executionTimes.slice(0, 5))
  
  const avg_latency = executionTimes.length > 0 
    ? executionTimes.reduce((sum, time) => sum + time, 0) / executionTimes.length
    : 0

  // Calculate TPS from transaction events
  const tpsValues = transactionMetrics
    .map(m => m.data?.tps_value)
    .filter((tps): tps is number => tps !== undefined)
  
  const currentTPS = tpsValues.length > 0 ? Math.max(...tpsValues) : qps // Fallback to QPS

  // Calculate error rate
  const errorCount = queryMetrics.filter(m => m.data?.status === 'ERROR' || m.data?.status === 'error').length
  const error_rate = queryMetrics.length > 0 
    ? (errorCount / queryMetrics.length) * 100
    : 0

  // Get latest system metrics - prefer the most recent one
  const systemMetricsArray = metrics
    .map(m => m.metrics)
    .filter((m): m is NonNullable<typeof m> => m !== undefined)
    
  const latestSystemMetrics = systemMetricsArray[0] // Already sorted by newest first

  const active_connections = latestSystemMetrics?.connection_pool_active ?? 0
  const idle_connections = latestSystemMetrics?.connection_pool_idle ?? 0
  const max_connections = latestSystemMetrics?.connection_pool_max ?? 0
  const pool_usage_ratio = latestSystemMetrics?.connection_pool_usage_ratio ?? 0
  const heap_usage_ratio = latestSystemMetrics?.heap_usage_ratio ?? 0
  const cpu_usage_ratio = latestSystemMetrics?.cpu_usage_ratio ?? 0

  const result = {
    qps: Math.round(qps * 100) / 100, // Round to 2 decimal places
    tps: Math.round(currentTPS * 100) / 100,
    avgLatency: Math.round(avg_latency * 100) / 100, // Use camelCase for consistency
    avg_latency: Math.round(avg_latency * 100) / 100, // Keep snake_case for backward compatibility
    error_rate: Math.round(error_rate * 100) / 100,
    transactionCount: queryMetrics.length,
    active_connections,
    activeConnections: active_connections, // camelCase version
    idle_connections,
    idleConnections: idle_connections, // camelCase version
    max_connections,
    maxConnections: max_connections, // camelCase version
    pool_usage_ratio: Math.round(pool_usage_ratio * 100) / 100,
    poolUsageRatio: Math.round(pool_usage_ratio * 100) / 100, // camelCase version
    heap_usage_ratio: Math.round(heap_usage_ratio * 100) / 100,
    cpu_usage_ratio: Math.round(cpu_usage_ratio * 100) / 100,
  }

  console.log('📊 Calculated new aggregated metrics: ', result)
  return result
}