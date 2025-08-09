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
          
          // Filter by event types if specified
          if (config.eventTypes.length > 0 && !config.eventTypes.includes(message.data.event_type)) {
            return
          }
          
          setMetrics(prev => {
            const newMetrics = [message.data, ...prev]
            return newMetrics.slice(0, config.maxMetrics)
          })
        } catch (error) {
          console.warn('Failed to parse WebSocket message:', error)
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

    return () => {
      mountedRef.current = false
      
      if (wsRef.current) {
        wsRef.current.close()
      }
      
      if (reconnectTimeoutRef.current) {
        clearTimeout(reconnectTimeoutRef.current)
      }
    }
  }, [connect])

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
  if (metrics.length === 0) {
    return {
      qps: 0,
      avg_latency: 0,
      error_rate: 0,
      active_connections: 0,
      max_connections: 0,
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

  const queryMetrics = recentMetrics.filter(m => m.data && m.event_type === 'query_execution')
  
  // Calculate QPS (queries per second)
  const qps = queryMetrics.length / 60

  // Calculate average latency
  const executionTimes = queryMetrics
    .map(m => m.data?.execution_time_ms)
    .filter((time): time is number => time !== undefined)
  
  const avg_latency = executionTimes.length > 0 
    ? executionTimes.reduce((sum, time) => sum + time, 0) / executionTimes.length
    : 0

  // Calculate error rate
  const errorCount = queryMetrics.filter(m => m.data?.status === 'ERROR').length
  const error_rate = queryMetrics.length > 0 
    ? (errorCount / queryMetrics.length) * 100
    : 0

  // Get latest system metrics
  const latestSystemMetrics = metrics
    .map(m => m.metrics)
    .filter((m): m is NonNullable<typeof m> => m !== undefined)[0]

  const active_connections = latestSystemMetrics?.connection_pool_active ?? 0
  const max_connections = latestSystemMetrics?.connection_pool_max ?? 0
  const heap_usage_ratio = latestSystemMetrics?.heap_usage_ratio ?? 0
  const cpu_usage_ratio = latestSystemMetrics?.cpu_usage_ratio ?? 0

  return {
    qps: Math.round(qps * 100) / 100, // Round to 2 decimal places
    avg_latency: Math.round(avg_latency * 100) / 100,
    error_rate: Math.round(error_rate * 100) / 100,
    active_connections,
    max_connections,
    heap_usage_ratio: Math.round(heap_usage_ratio * 100) / 100,
    cpu_usage_ratio: Math.round(cpu_usage_ratio * 100) / 100,
  }
}