'use client'

import { useState, useEffect } from 'react'

interface QueryMetrics {
  timestamp: string
  pod_name?: string
  event_type: string
  data?: {
    query_id: string
    sql_pattern?: string
    sql_type?: string
    table_names?: string[]
    execution_time_ms?: number
    status: string
    error_message?: string
  }
  metrics?: {
    connection_pool_active?: number
    connection_pool_idle?: number
    connection_pool_max?: number
    connection_pool_usage_ratio?: number
    heap_used_mb?: number
    heap_max_mb?: number
    heap_usage_ratio?: number
    cpu_usage_ratio?: number
  }
}

interface AggregatedMetrics {
  qps: number
  avgLatency: number
  activeConnections: number
  errorRate: number
}

export default function Dashboard() {
  const [metrics, setMetrics] = useState<QueryMetrics[]>([])
  const [isConnected, setIsConnected] = useState(false)
  const [aggregatedMetrics, setAggregatedMetrics] = useState<AggregatedMetrics>({
    qps: 0,
    avgLatency: 0,
    activeConnections: 0,
    errorRate: 0
  })

  useEffect(() => {
    console.log('🚀 Starting KubeDB Monitor Dashboard')
    
    // Try WebSocket connection first
    const wsUrl = window.location.protocol === 'https:' 
      ? `wss://${window.location.host}/ws`
      : `ws://${window.location.host.replace(':3000', ':8080')}/ws`
    
    console.log(`🔗 WebSocket URL: ${wsUrl}`)
    
    connectWebSocket(wsUrl)
  }, [])

  const connectWebSocket = (wsUrl: string) => {
    let ws: WebSocket | null = null
    let reconnectTimer: NodeJS.Timeout | null = null

    const connect = () => {
      try {
        console.log('🔗 Attempting WebSocket connection to:', wsUrl)
        ws = new WebSocket(wsUrl)
        
        ws.onopen = () => {
          console.log('✅ WebSocket connected successfully!')
          setIsConnected(true)
          if (reconnectTimer) {
            clearTimeout(reconnectTimer)
            reconnectTimer = null
          }
        }
        
        ws.onmessage = (event) => {
          try {
            const message = JSON.parse(event.data)
            const newMetric: QueryMetrics = message.data
            processMetric(newMetric)
          } catch (error) {
            console.error('Failed to parse WebSocket message:', error)
          }
        }
        
        ws.onclose = (event) => {
          console.log('🔌 WebSocket disconnected', { code: event.code, reason: event.reason })
          setIsConnected(false)
          
          // Auto-reconnect after 5 seconds
          if (!reconnectTimer) {
            reconnectTimer = setTimeout(() => {
              console.log('🔄 Attempting to reconnect...')
              connect()
            }, 5000)
          }
        }
        
        ws.onerror = (error) => {
          console.error('❌ WebSocket error:', error)
          setIsConnected(false)
        }
      } catch (error) {
        console.error('Failed to create WebSocket:', error)
        if (!reconnectTimer) {
          reconnectTimer = setTimeout(connect, 5000)
        }
      }
    }

    connect()
  }

  const processMetric = (newMetric: QueryMetrics) => {
    setMetrics(prev => {
      const updated = [newMetric, ...prev].slice(0, 100)
      calculateAndSetAggregatedMetrics(updated)
      return updated
    })
  }

  const calculateAndSetAggregatedMetrics = (updatedMetrics: QueryMetrics[]) => {
    // Calculate aggregated metrics
    const now = Date.now()
    const oneMinuteAgo = now - 60000
    const recentMetrics = updatedMetrics.filter(m => 
      new Date(m.timestamp).getTime() > oneMinuteAgo
    )
    
    const queryMetrics = recentMetrics.filter(m => 
      m.event_type === 'query_execution' && m.data
    )
    
    const qps = queryMetrics.length / 60
    
    const executionTimes = queryMetrics
      .map(m => m.data?.execution_time_ms)
      .filter((time): time is number => time !== undefined && time > 0)
    
    const avgLatency = executionTimes.length > 0
      ? executionTimes.reduce((sum, time) => sum + time, 0) / executionTimes.length
      : 0
    
    const errors = queryMetrics.filter(m => m.data?.status === 'ERROR')
    const errorRate = queryMetrics.length > 0 
      ? (errors.length / queryMetrics.length) * 100 
      : 0
    
    const latestMetric = updatedMetrics.find(m => m.metrics)
    const activeConnections = latestMetric?.metrics?.connection_pool_active ?? 0
    
    setAggregatedMetrics({
      qps: Math.round(qps * 100) / 100,
      avgLatency: Math.round(avgLatency * 100) / 100,
      activeConnections,
      errorRate: Math.round(errorRate * 100) / 100
    })
  }

  const recentQueries = metrics
    .filter(m => m.event_type === 'query_execution' && m.data)
    .slice(0, 5)

  return (
    <div className="min-h-screen bg-gray-900 text-green-400 p-8">
      <div className="max-w-7xl mx-auto">
        {/* Header */}
        <header className="mb-8">
          <h1 className="text-4xl font-bold text-center mb-4">
            🚀 KubeDB Monitor Dashboard
          </h1>
          <div className="text-center">
            <span className={`inline-flex items-center px-3 py-1 rounded-full text-sm ${
              isConnected 
                ? 'bg-green-900 text-green-300' 
                : 'bg-red-900 text-red-300'
            }`}>
              <div className={`w-2 h-2 rounded-full mr-2 ${
                isConnected ? 'bg-green-400' : 'bg-red-400'
              }`}></div>
              {isConnected ? 'Connected' : 'Disconnected'}
            </span>
          </div>
        </header>

        {/* Metrics Cards */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
          <MetricCard
            title="QPS"
            value={aggregatedMetrics.qps.toString()}
            unit="queries/sec"
            isAnimated={isConnected}
          />
          <MetricCard
            title="Avg Latency"
            value={aggregatedMetrics.avgLatency.toString()}
            unit="ms"
            isAnimated={isConnected}
          />
          <MetricCard
            title="Active Connections"
            value={aggregatedMetrics.activeConnections.toString()}
            unit="connections"
            isAnimated={isConnected}
          />
          <MetricCard
            title="Error Rate"
            value={aggregatedMetrics.errorRate.toString()}
            unit="%"
            isAnimated={isConnected}
          />
        </div>

        {/* Query Flow Animation */}
        <div className="mb-8">
          <h2 className="text-2xl font-semibold mb-4 text-center">
            📊 Real-time Query Flow
          </h2>
          <div className="bg-gray-800 rounded-lg p-6 border border-green-800">
            <QueryFlowVisualization 
              isConnected={isConnected} 
              recentQueries={recentQueries}
            />
          </div>
        </div>

        {/* Recent Queries */}
        <div>
          <h2 className="text-2xl font-semibold mb-4 text-center">
            📋 Recent Queries
          </h2>
          <div className="bg-gray-800 rounded-lg border border-green-800">
            <div className="p-4 overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-green-800">
                    <th className="text-left p-2">Time</th>
                    <th className="text-left p-2">Type</th>
                    <th className="text-left p-2">Pattern</th>
                    <th className="text-left p-2">Latency</th>
                    <th className="text-left p-2">Status</th>
                  </tr>
                </thead>
                <tbody>
                  {recentQueries.map((query, index) => (
                    <tr 
                      key={`${query.data?.query_id}-${index}`}
                      className="border-b border-gray-700 hover:bg-gray-750"
                    >
                      <td className="p-2">
                        {new Date(query.timestamp).toLocaleTimeString()}
                      </td>
                      <td className="p-2">{query.data?.sql_type || 'N/A'}</td>
                      <td className="p-2 font-mono text-xs">
                        {query.data?.sql_pattern?.substring(0, 50) || 'N/A'}...
                      </td>
                      <td className="p-2">
                        {query.data?.execution_time_ms || 0}ms
                      </td>
                      <td className="p-2">
                        <span className={`px-2 py-1 rounded text-xs ${
                          query.data?.status === 'SUCCESS' 
                            ? 'bg-green-900 text-green-300' 
                            : 'bg-red-900 text-red-300'
                        }`}>
                          {query.data?.status || 'UNKNOWN'}
                        </span>
                      </td>
                    </tr>
                  ))}
                  {recentQueries.length === 0 && (
                    <tr>
                      <td colSpan={5} className="text-center p-8 text-gray-500">
                        No queries yet... Waiting for data 📊
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

function MetricCard({ 
  title, 
  value, 
  unit, 
  isAnimated 
}: { 
  title: string
  value: string
  unit: string
  isAnimated: boolean
}) {
  return (
    <div className={`bg-gray-800 rounded-lg p-6 border border-green-800 ${
      isAnimated ? 'animate-pulse' : ''
    }`}>
      <h3 className="text-sm font-medium text-gray-400 mb-2">{title}</h3>
      <div className="flex items-end space-x-2">
        <span className="text-3xl font-bold text-green-400">{value}</span>
        <span className="text-sm text-gray-500">{unit}</span>
      </div>
    </div>
  )
}

function QueryFlowVisualization({ 
  isConnected, 
  recentQueries 
}: { 
  isConnected: boolean
  recentQueries: QueryMetrics[]
}) {
  return (
    <div className="flex items-center justify-center space-x-8 h-32">
      <div className="flex flex-col items-center">
        <div className={`w-16 h-16 rounded-full border-2 flex items-center justify-center ${
          isConnected ? 'border-green-400 bg-green-900' : 'border-gray-600 bg-gray-700'
        }`}>
          <span className="text-2xl">📱</span>
        </div>
        <span className="text-xs mt-2">App</span>
      </div>
      
      <div className={`flex-1 h-1 ${
        isConnected ? 'bg-green-400' : 'bg-gray-600'
      } ${isConnected ? 'animate-pulse' : ''}`}></div>
      
      <div className="flex flex-col items-center">
        <div className={`w-16 h-16 rounded-full border-2 flex items-center justify-center ${
          isConnected ? 'border-green-400 bg-green-900' : 'border-gray-600 bg-gray-700'
        }`}>
          <span className="text-2xl">🏊</span>
        </div>
        <span className="text-xs mt-2">Pool</span>
      </div>
      
      <div className={`flex-1 h-1 ${
        isConnected ? 'bg-green-400' : 'bg-gray-600'
      } ${isConnected ? 'animate-pulse' : ''}`}></div>
      
      <div className="flex flex-col items-center">
        <div className={`w-16 h-16 rounded-full border-2 flex items-center justify-center ${
          isConnected ? 'border-green-400 bg-green-900' : 'border-gray-600 bg-gray-700'
        }`}>
          <span className="text-2xl">🗄️</span>
        </div>
        <span className="text-xs mt-2">Database</span>
      </div>
      
      {isConnected && recentQueries.length > 0 && (
        <div className="absolute">
          <div className="w-4 h-4 rounded-full bg-green-400 animate-bounce"></div>
        </div>
      )}
    </div>
  )
}