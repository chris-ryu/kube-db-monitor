'use client'

import { useState, useEffect } from 'react'
import { QueryMetrics } from '@/types/metrics'
import { TransactionEvent } from '@/types/transaction'
import { DeadlockEvent } from '@/types/deadlock'
import { DeadlockAlert } from '@/components/DeadlockAlert'
import { TransactionTimeline } from '@/components/TransactionTimeline'
import { LongRunningTransactionAlert } from '@/components/LongRunningTransactionAlert'
import { NodePodMetrics } from '@/components/NodePodMetrics'

interface AggregatedMetrics {
  qps: number
  avgLatency: number
  activeConnections: number
  errorRate: number
  transactionCount: number
  tps: number // Transactions Per Second
}

export default function Dashboard() {
  const [metrics, setMetrics] = useState<QueryMetrics[]>([])
  const [transactions, setTransactions] = useState<TransactionEvent[]>([])
  const [deadlocks, setDeadlocks] = useState<DeadlockEvent[]>([])
  const [isConnected, setIsConnected] = useState(false)
  const [aggregatedMetrics, setAggregatedMetrics] = useState<AggregatedMetrics>({
    qps: 0,
    avgLatency: 0,
    activeConnections: 0,
    errorRate: 0,
    transactionCount: 0,
    tps: 0
  })

  useEffect(() => {
    console.log('🚀 Starting Advanced KubeDB Monitor Dashboard')
    
    // Always try WebSocket connection first
    const useWebSocket = true
    const wsUrl = process.env.NEXT_PUBLIC_WS_URL || 
      (window.location.hostname === 'localhost' 
        ? 'ws://localhost:8081/ws'
        : `wss://kube-db-mon-dashboard.bitgaram.info/ws`)
    
    console.log(`📍 Environment: ${process.env.NODE_ENV}, Host: ${window.location.hostname}`)
    console.log(`🔗 WebSocket URL: ${wsUrl}, Use WebSocket: ${useWebSocket}`)
    
    if (useWebSocket) {
      connectWebSocket(wsUrl)
    } else {
      console.log('⏳ WebSocket disabled - waiting for real data connection')
      // Only generate mock data when WebSocket is disabled
      generateMockDataForDemo()
    }
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
            console.log('📨 Received WebSocket message:', message.type, message)
            processWebSocketMessage(message)
          } catch (error) {
            console.error('❌ Failed to parse WebSocket message:', error, event.data)
          }
        }
        
        ws.onclose = (event) => {
          console.log('🔌 WebSocket disconnected', { code: event.code, reason: event.reason })
          setIsConnected(false)
          
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

  const processWebSocketMessage = (message: any) => {
    console.log('🔍 Processing WebSocket message type:', message.type)
    // Handle different types of WebSocket messages
    if (message.type === 'query_metrics' || message.type === 'metric' || message.type === 'query_execution') {
      console.log('📊 Processing query metrics:', message.data)
      processMetric(message.data)
    } else if (message.type === 'transaction_event') {
      console.log('🔄 Processing transaction event:', message.data)
      processTransactionEvent(message.data)
    } else if (message.type === 'deadlock_event') {
      console.log('⚠️ Processing deadlock event:', message.data)
      console.log('🔍 Full message structure:', JSON.stringify(message, null, 2))
      processDeadlockEvent(message.data)
    } else {
      console.warn('❓ Unknown message type:', message.type, message)
    }
  }

  const processMetric = (newMetric: QueryMetrics) => {
    console.log('➕ Adding new metric to state:', newMetric.event_type, newMetric.data?.query_id)
    
    // Handle TPS events - convert to TransactionEvent for Timeline display
    if (newMetric.event_type === 'tps_event') {
      console.log('🚀 Processing TPS event:', newMetric)
      const tpsValue = newMetric.data?.tps_value || newMetric.data?.execution_time_ms || 0
      const transactionEvent: TransactionEvent = {
        id: `tx-tps-${Date.now()}`,
        transaction_id: `TPS-${tpsValue.toFixed(1)}`,
        start_time: new Date().toISOString(),
        status: 'committed',
        duration_ms: Math.floor(tpsValue * 100), // Visual representation
        query_count: Math.ceil(tpsValue),
        total_execution_time_ms: Math.floor(tpsValue * 50),
        pod_name: newMetric.pod_name || 'unknown-pod',
        namespace: 'production',
        queries: [{
          query_id: `tps-query-${Date.now()}`,
          sequence_number: 1,
          sql_pattern: `High TPS detected: ${tpsValue.toFixed(1)} queries/second`,
          sql_type: 'TPS_EVENT',
          execution_time_ms: Math.floor(tpsValue * 10),
          timestamp: new Date().toISOString(),
          status: 'success'
        }]
      }
      processTransactionEvent(transactionEvent)
    }
    
    // Handle Long Running Transaction events
    if (newMetric.event_type === 'long_running_transaction') {
      console.log('🐌 Processing Long Running Transaction event:', newMetric)
      const transactionEvent: TransactionEvent = {
        id: `tx-long-${Date.now()}`,
        transaction_id: newMetric.data?.transaction_id || `tx-${Date.now()}`,
        start_time: new Date(Date.now() - (newMetric.data?.transaction_duration || 7000)).toISOString(),
        status: 'active',
        duration_ms: newMetric.data?.transaction_duration || 7000,
        query_count: 1,
        total_execution_time_ms: Math.floor((newMetric.data?.transaction_duration || 7000) * 0.7),
        pod_name: newMetric.pod_name || 'unknown-pod',
        namespace: 'production',
        queries: []
      }
      processTransactionEvent(transactionEvent)
    }
    
    setMetrics(prev => {
      const updated = [newMetric, ...prev].slice(0, 200)
      console.log('📈 Metrics state updated, total metrics:', updated.length)
      calculateAndSetAggregatedMetrics(updated)
      
      return updated
    })
  }

  const processTransactionEvent = (transactionEvent: TransactionEvent) => {
    setTransactions(prev => {
      const existing = prev.find(t => t.transaction_id === transactionEvent.transaction_id)
      if (existing) {
        // Update existing transaction
        return prev.map(t => 
          t.transaction_id === transactionEvent.transaction_id ? transactionEvent : t
        )
      } else {
        // Add new transaction
        return [transactionEvent, ...prev].slice(0, 100)
      }
    })
  }

  const processDeadlockEvent = (rawDeadlockData: any) => {
    console.log('🔍 Raw deadlock data received:', rawDeadlockData)
    console.log('🔍 Raw data type:', typeof rawDeadlockData)
    console.log('🔍 Raw data keys:', Object.keys(rawDeadlockData || {}))
    
    // Control Plane sends deadlock data directly in message.data (not nested)
    // WebSocket 메시지에서 DeadlockEvent 구조로 변환
    const deadlockEvent: DeadlockEvent = {
      id: rawDeadlockData?.id || `deadlock-${Date.now()}`,
      participants: rawDeadlockData?.participants || [],
      detectionTime: rawDeadlockData?.detectionTime || rawDeadlockData?.timestamp || new Date().toISOString(),
      recommendedVictim: rawDeadlockData?.recommendedVictim || 'unknown',
      lockChain: rawDeadlockData?.lockChain || [],
      severity: rawDeadlockData?.severity || 'critical',
      status: rawDeadlockData?.status || 'active',
      pod_name: rawDeadlockData?.pod_name,
      namespace: rawDeadlockData?.namespace || 'unknown',
      cycleLength: rawDeadlockData?.cycleLength || 2
    }
    
    console.log('🎯 Converted deadlock event:', deadlockEvent)
    console.log('🎯 Deadlock event participants:', deadlockEvent.participants)
    console.log('🎯 Deadlock event lockChain:', deadlockEvent.lockChain)
    
    setDeadlocks(prev => {
      // Check if deadlock with same ID already exists
      const existingIndex = prev.findIndex(d => d.id === deadlockEvent.id)
      
      if (existingIndex >= 0) {
        // Update existing deadlock
        console.log('🔄 Updating existing deadlock:', deadlockEvent.id)
        const updated = [...prev]
        updated[existingIndex] = deadlockEvent
        return updated
      } else {
        // Add new deadlock
        const updated = [deadlockEvent, ...prev].slice(0, 50)
        console.log('📊 Added new deadlock:', deadlockEvent.id)
        console.log('📊 Updated deadlocks state:', updated.length, 'total deadlocks')
        console.log('📊 First deadlock in state:', updated[0])
        return updated
      }
    })
  }

  const calculateAndSetAggregatedMetrics = (updatedMetrics: QueryMetrics[]) => {
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
    
    // Calculate transactions per second based on query metrics
    // For demo purposes, consider successful queries as completed transactions
    const successfulQueries = queryMetrics.filter(m => 
      m.data?.status === 'SUCCESS'
    )
    const tps = successfulQueries.length / 60
    
    const newMetrics = {
      qps: Math.round(qps * 100) / 100,
      avgLatency: Math.round(avgLatency * 100) / 100,
      activeConnections,
      errorRate: Math.round(errorRate * 100) / 100,
      transactionCount: Math.max(0, Math.floor(queryMetrics.length / 10)), // Simulate active transactions based on recent query activity
      tps: Math.round(tps * 100) / 100
    }
    
    console.log('🎯 Calculated new aggregated metrics:', newMetrics)
    setAggregatedMetrics(newMetrics)
  }


  // Mock data generation for demo
  const generateMockDataForDemo = () => {
    // Generate mock transactions
    const mockTransactions: TransactionEvent[] = [
      {
        id: 'evt-1',
        transaction_id: 'tx-slow-user-registration',
        start_time: new Date(Date.now() - 8 * 60000).toISOString(), // 8 minutes ago
        status: 'active',
        duration_ms: 8 * 60000, // 8 minutes - long running
        query_count: 12,
        total_execution_time_ms: 6 * 60000,
        pod_name: 'registration-service-1',
        namespace: 'production',
        queries: [
          {
            query_id: 'q-1',
            sql_pattern: 'SELECT * FROM users WHERE email = ? FOR UPDATE',
            sql_type: 'SELECT',
            execution_time_ms: 45000,
            timestamp: new Date(Date.now() - 7.5 * 60000).toISOString(),
            sequence_number: 1,
            status: 'success'
          }
        ]
      },
      {
        id: 'evt-2',
        transaction_id: 'tx-batch-enrollment',
        start_time: new Date(Date.now() - 2 * 60000).toISOString(),
        end_time: new Date(Date.now() - 30000).toISOString(),
        status: 'committed',
        duration_ms: 90000, // 1.5 minutes
        query_count: 25,
        total_execution_time_ms: 75000,
        pod_name: 'enrollment-service-2',
        namespace: 'production',
        queries: []
      }
    ]

    // Generate mock deadlock
    const mockDeadlock: DeadlockEvent = {
      id: 'dl-demo-1',
      participants: ['tx-user-update-1', 'tx-enrollment-batch-2'],
      detectionTime: new Date(Date.now() - 30000).toISOString(), // 30 seconds ago
      recommendedVictim: 'tx-enrollment-batch-2',
      lockChain: [
        'tx-user-update-1 → users table (row_id: 123)',
        'tx-enrollment-batch-2 → enrollments table (user_id: 123)',
        'tx-enrollment-batch-2 → users table (row_id: 123) [BLOCKED]'
      ],
      severity: 'critical',
      status: 'active',
      pod_name: 'enrollment-service-2',
      namespace: 'production',
      cycleLength: 2
    }

    setTransactions(mockTransactions)
    setDeadlocks([mockDeadlock])
  }

  const handleResolveDeadlock = (deadlockId: string) => {
    setDeadlocks(prev => prev.map(d => 
      d.id === deadlockId 
        ? { ...d, status: 'resolved' as const, resolvedAt: new Date().toISOString() }
        : d
    ))
    console.log(`🔧 Resolving deadlock: ${deadlockId}`)
  }

  const handleKillTransaction = (transactionId: string) => {
    setTransactions(prev => prev.map(t => 
      t.transaction_id === transactionId
        ? { ...t, status: 'rolled_back' as const, end_time: new Date().toISOString() }
        : t
    ))
    console.log(`💀 Killing transaction: ${transactionId}`)
  }

  const longRunningTransactions = transactions.filter(t => 
    t.status === 'active' && t.duration_ms && t.duration_ms > 5 * 1000 // > 5 seconds
  )

  return (
    <div className="min-h-screen bg-gray-900 text-green-400 p-8">
      <div className="max-w-7xl mx-auto">
        {/* Header */}
        <header className="mb-8">
          <h1 className="text-4xl font-bold text-center mb-4">
            🚀 Advanced KubeDB Monitor Dashboard
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
              {isConnected ? 'Connected' : 'Demo Mode'}
            </span>
          </div>
        </header>

        {/* Enhanced Metrics Cards */}
        <div className="grid grid-cols-1 md:grid-cols-3 lg:grid-cols-6 gap-6 mb-8">
          <MetricCard
            title="QPS"
            value={aggregatedMetrics.qps.toString()}
            unit="queries/sec"
            isAnimated={isConnected}
          />
          <MetricCard
            title="TPS"
            value={aggregatedMetrics.tps.toString()}
            unit="tx/sec"
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
            title="Active Transactions"
            value={aggregatedMetrics.transactionCount.toString()}
            unit="transactions"
            isAnimated={isConnected}
          />
          <MetricCard
            title="Error Rate"
            value={aggregatedMetrics.errorRate.toString()}
            unit="%"
            isAnimated={isConnected}
          />
        </div>

        {/* Alert Panels */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8 mb-8">
          <DeadlockAlert 
            deadlocks={deadlocks} 
            onResolve={handleResolveDeadlock}
          />
          <LongRunningTransactionAlert 
            transactions={longRunningTransactions}
            onKillTransaction={handleKillTransaction}
            thresholdSeconds={5}
          />
        </div>

        {/* Node/Pod Metrics Panel */}
        <div className="mb-8">
          <NodePodMetrics metrics={metrics} />
        </div>

        {/* Transaction Timeline */}
        <div className="mb-8">
          <TransactionTimeline transactions={transactions} />
        </div>

        {/* Recent Queries Table */}
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
                    <th className="text-left p-2">Pod</th>
                    <th className="text-left p-2">Type</th>
                    <th className="text-left p-2">Pattern</th>
                    <th className="text-left p-2">Latency</th>
                    <th className="text-left p-2">Status</th>
                  </tr>
                </thead>
                <tbody>
                  {metrics
                    .filter(m => (m.event_type === 'query_execution' || m.event_type === 'tps_event' || m.event_type === 'long_running_transaction') && m.data)
                    .slice(0, 10)
                    .map((query, index) => (
                    <tr 
                      key={`${query.data?.query_id}-${index}`}
                      className="border-b border-gray-700 hover:bg-gray-750"
                    >
                      <td className="p-2">
                        {new Date(query.timestamp).toLocaleTimeString()}
                      </td>
                      <td className="p-2">
                        <span className="bg-gray-700 text-gray-300 px-2 py-1 rounded text-xs">
                          {query.pod_name || 'N/A'}
                        </span>
                      </td>
                      <td className="p-2">
                        {query.event_type === 'tps_event' ? 'TPS_EVENT' : 
                         query.event_type === 'long_running_transaction' ? 'LONG_TX' : 
                         query.data?.sql_type || 'OTHER'}
                      </td>
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
                  {metrics.length === 0 && (
                    <tr>
                      <td colSpan={6} className="text-center p-8 text-gray-500">
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
        <span className="text-2xl font-bold text-green-400">{value}</span>
        <span className="text-xs text-gray-500">{unit}</span>
      </div>
    </div>
  )
}