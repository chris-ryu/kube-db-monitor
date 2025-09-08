'use client'

import { useState, useEffect } from 'react'
import { QueryMetrics, AggregatedMetrics } from '@/types/metrics'
import { TransactionEvent } from '@/types/transaction'
import { DeadlockEvent } from '@/types/deadlock'
import { DeadlockAlert } from '@/components/DeadlockAlert'
import { TransactionTimeline } from '@/components/TransactionTimeline'
import { LongRunningTransactionAlert } from '@/components/LongRunningTransactionAlert'
import { ConnectionPoolDetail } from '@/components/ConnectionPoolDetail'
import { LivePerformanceChart } from '@/components/LivePerformanceChart'
import { 
  Activity, 
  Database, 
  Clock, 
  Users, 
  Zap, 
  AlertTriangle 
} from "lucide-react"


export default function Dashboard() {
  // 동적 설정을 위한 state
  const [dashboardConfig, setDashboardConfig] = useState({
    title: '🚀 Advanced KubeDB Monitor Dashboard',
    longRunningThresholdMs: 4000
  })
  
  const [metrics, setMetrics] = useState<QueryMetrics[]>([])
  const [transactions, setTransactions] = useState<TransactionEvent[]>([])
  const [deadlocks, setDeadlocks] = useState<DeadlockEvent[]>([])
  const [isConnected, setIsConnected] = useState(false)
  const [aggregatedMetrics, setAggregatedMetrics] = useState<AggregatedMetrics>({
    qps: 0,
    avg_latency: 0,
    avgLatency: 0,
    active_connections: 0,
    activeConnections: 0,
    idle_connections: 0,
    idleConnections: 0,
    max_connections: 0,
    maxConnections: 0,
    pool_usage_ratio: 0,
    poolUsageRatio: 0,
    error_rate: 0,
    transactionCount: 0,
    tps: 0,
    heap_usage_ratio: 0,
    cpu_usage_ratio: 0,
    // Advanced Connection Pool metrics
    peakActiveConnections: 0,
    poolHealthScore: 0,
    connectionRequestsPerSecond: 0,
    averageHoldTime: 0,
    waitingThreads: 0
  })

  useEffect(() => {
    // 서버사이드에서 주입된 런타임 설정 사용
    const runtimeConfig = (window as any).__RUNTIME_CONFIG__
    
    if (runtimeConfig && runtimeConfig.title) {
      setDashboardConfig(runtimeConfig)
      console.log(`🚀 Starting ${runtimeConfig.title} (from server-side runtime config)`)
      console.log(`📊 Long running threshold: ${runtimeConfig.longRunningThresholdMs}ms`)
    } else {
      console.error('❌ No runtime config found on window object')
      // Fallback
      const fallbackConfig = {
        title: '🚀 FlowLight DB Monitor Dashboard', // 현재 ConfigMap 값
        longRunningThresholdMs: 4000
      }
      setDashboardConfig(fallbackConfig)
      console.log(`🚀 Starting ${fallbackConfig.title} (fallback)`)
    }
    
    // Always try WebSocket connection first
    const useWebSocket = true
    const wsUrl = process.env.NEXT_PUBLIC_WS_URL || 
      (window.location.hostname === 'localhost' 
        ? 'ws://localhost:8081/ws'
        : 'wss://kube-db-mon-controlplane.bitgaram.info/ws')
    
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
    console.log('🔍 Full WebSocket message structure:', JSON.stringify(message, null, 2))
    
    // Handle different types of WebSocket messages
    if (message.type === 'query_metrics' || message.type === 'metric' || message.type === 'query_execution') {
      console.log('📊 Processing query metrics:', message.data)
      // message.data is the full QueryMetrics object from Control-plane
      processMetric(message.data)
    } else if (message.type === 'transaction_event') {
      console.log('🔄 Processing transaction event:', message.data)
      processTransactionEvent(message.data)
    } else if (message.type === 'deadlock_event') {
      console.log('⚠️ Processing deadlock event:', message.data)
      console.log('🔍 Full deadlock message structure:', JSON.stringify(message, null, 2))
      processDeadlockEvent(message.data)
    } else if (message.type === 'long_running_transaction') {
      // Long Running Transaction은 event_type을 통해서만 처리 (중복 제거)
      console.log('🐌 Long running transaction message received - processing via event_type only')
      processMetric(message.data)
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
        // Agent 필수 필드들
        transaction_duration: Math.floor(tpsValue * 100),
        sql_pattern: `High TPS detected: ${tpsValue.toFixed(1)} queries/second`,
        execution_time_ms: Math.floor(tpsValue * 10),
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
      console.log('🔍 SQL Info - CurrentQuery:', newMetric.data?.current_query)
      console.log('🔍 SQL Info - StoredProcedure:', newMetric.data?.stored_procedure)
      console.log('🔍 SQL Info - QueryHistory:', newMetric.data?.query_history)
      
      const transactionEvent: TransactionEvent = {
        id: `tx-long-${Date.now()}`,
        transaction_id: newMetric.data?.transaction_id || `tx-${Date.now()}`,
        start_time: new Date(Date.now() - (newMetric.data?.transaction_duration || 7000)).toISOString(),
        status: 'active',
        duration_ms: newMetric.data?.transaction_duration || 7000,
        query_count: (newMetric.data?.query_history?.length || 0) + 1,
        total_execution_time_ms: Math.floor((newMetric.data?.transaction_duration || 7000) * 0.7),
        pod_name: newMetric.pod_name || 'unknown-pod',
        namespace: newMetric.namespace || 'production',
        
        // Agent 필수 필드들
        transaction_duration: newMetric.data?.transaction_duration || 7000,
        sql_pattern: newMetric.data?.sql_pattern || 'Long running transaction detected',
        execution_time_ms: newMetric.data?.execution_time_ms || 7000,
        
        // 🎯 SQL 쿼리 정보 매핑 추가
        current_query: newMetric.data?.current_query || undefined,
        stored_procedure: newMetric.data?.stored_procedure || undefined,
        query_history: newMetric.data?.query_history || [],
        
        queries: newMetric.data?.query_history ? newMetric.data.query_history.map((qh: any, index: number) => ({
          query_id: `hist-${index}`,
          sql_pattern: qh.query || 'Unknown Query',
          sql_type: qh.query_type || 'OTHER' as any,
          execution_time_ms: qh.execution_time || 0,
          timestamp: new Date(qh.start_time || Date.now()).toISOString(),
          sequence_number: index,
          status: 'success' as any
        })) : []
      }
      
      console.log('✅ Created TransactionEvent with SQL info:', {
        current_query: transactionEvent.current_query ? 'Present' : 'Missing',
        stored_procedure: transactionEvent.stored_procedure ? 'Present' : 'Missing', 
        query_history_count: transactionEvent.query_history?.length || 0,
        queries_count: transactionEvent.queries.length
      })
      
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
      cycleLength: rawDeadlockData?.cycleLength || 2,
      // Control Plane 필수 필드들
      duration_ms: rawDeadlockData?.duration_ms || rawDeadlockData?.deadlockDuration || 5000,
      connections: rawDeadlockData?.connections || rawDeadlockData?.deadlockConnections || 'unknown'
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
    console.log('🎯 Calculating aggregated metrics with', updatedMetrics.length, 'total metrics')
    
    const now = Date.now()
    const oneMinuteAgo = now - 60000
    const recentMetrics = updatedMetrics.filter(m => 
      new Date(m.timestamp).getTime() > oneMinuteAgo
    )
    
    console.log('📊 Recent metrics (last 1 min):', recentMetrics.length)
    
    const queryMetrics = recentMetrics.filter(m => 
      m.event_type === 'query_execution' && m.data
    )
    
    console.log('🔍 Query metrics found:', queryMetrics.length)
    queryMetrics.forEach((m, i) => {
      console.log(`  Query ${i+1}: type=${m.data?.sql_type}, time=${m.data?.execution_time_ms}ms, status=${m.data?.status}`)
    })
    
    const qps = queryMetrics.length / 60
    
    const executionTimes = queryMetrics
      .map(m => m.data?.execution_time_ms)
      .filter((time): time is number => time !== undefined && time > 0)
    
    console.log('⏱️ Execution times:', executionTimes)
    
    const avgLatency = executionTimes.length > 0
      ? executionTimes.reduce((sum, time) => sum + time, 0) / executionTimes.length
      : 0
    
    // Fix status check - Control-plane sends 'completed', not 'ERROR'
    const errors = queryMetrics.filter(m => 
      m.data?.status === 'ERROR' || m.data?.status === 'error'
    )
    const errorRate = queryMetrics.length > 0 
      ? (errors.length / queryMetrics.length) * 100 
      : 0
    
    const latestMetric = updatedMetrics.find(m => m.metrics)
    const activeConnections = latestMetric?.metrics?.connection_pool_active ?? 0
    const idleConnections = latestMetric?.metrics?.connection_pool_idle ?? 0
    const maxConnections = latestMetric?.metrics?.connection_pool_max ?? 0
    const poolUsageRatio = latestMetric?.metrics?.connection_pool_usage_ratio ?? 0
    
    // 고급 Connection Pool 메트릭
    const peakActiveConnections = latestMetric?.metrics?.connection_pool_peak_active ?? 0
    const peakTimestamp = latestMetric?.metrics?.connection_pool_peak_timestamp ?? 0
    const connectionRequestsPerSecond = latestMetric?.metrics?.connection_pool_requests_per_second ?? 0
    const poolHealthScore = latestMetric?.metrics?.connection_pool_health_score ?? 0
    const averageHoldTime = latestMetric?.metrics?.connection_pool_average_hold_time ?? 0
    const waitingThreads = latestMetric?.metrics?.connection_pool_waiting_threads ?? 0
    
    // Calculate transactions per second based on query metrics
    // Fix status check - Control-plane sends 'completed', not 'SUCCESS'
    const successfulQueries = queryMetrics.filter(m => 
      m.data?.status === 'completed' || m.data?.status === 'SUCCESS'
    )
    const tps = successfulQueries.length / 60
    
    console.log('✅ Successful queries:', successfulQueries.length)
    
    const newMetrics: AggregatedMetrics = {
      qps: Math.round(qps * 100) / 100,
      avg_latency: Math.round(avgLatency * 100) / 100,
      avgLatency: Math.round(avgLatency * 100) / 100,
      active_connections: activeConnections,
      activeConnections,
      idle_connections: idleConnections,
      idleConnections,
      max_connections: maxConnections,
      maxConnections,
      pool_usage_ratio: Math.round(poolUsageRatio * 100) / 100,
      poolUsageRatio: Math.round(poolUsageRatio * 100) / 100,
      error_rate: Math.round(errorRate * 100) / 100,
      transactionCount: Math.max(0, Math.floor(queryMetrics.length / 10)), // Simulate active transactions based on recent query activity
      tps: Math.round(tps * 100) / 100,
      heap_usage_ratio: 0,
      cpu_usage_ratio: 0,
      // Advanced Connection Pool metrics (camelCase for component compatibility)
      peak_active_connections: peakActiveConnections,
      peakActiveConnections,
      peak_timestamp: peakTimestamp,
      peakTimestamp,
      connection_requests_per_second: connectionRequestsPerSecond,
      connectionRequestsPerSecond,
      pool_health_score: poolHealthScore,
      poolHealthScore,
      average_hold_time: Math.round(averageHoldTime * 100) / 100,
      averageHoldTime: Math.round(averageHoldTime * 100) / 100,
      waiting_threads: waitingThreads,
      waitingThreads
    }
    
    console.log('🎯 Calculated new aggregated metrics:', newMetrics)
    console.log('🔄 Setting aggregated metrics state...')
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
        // Agent 필수 필드들
        transaction_duration: 8 * 60000,
        sql_pattern: 'SELECT * FROM users WHERE email = ? FOR UPDATE',
        execution_time_ms: 8 * 60000,
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
        // Agent 필수 필드들
        transaction_duration: 90000,
        sql_pattern: 'INSERT INTO enrollments (student_id, course_id) VALUES (?, ?)',
        execution_time_ms: 90000,
        queries: []
      }
    ]

    // Generate mock deadlock
    const mockDeadlock: DeadlockEvent = {
      id: 'dl-demo-1',
      participants: ['tx-user-update-1', 'tx-enrollment-batch-2'],
      detectionTime: new Date(Date.now() - 30000).toISOString(), // 30 seconds ago
      recommendedVictim: 'tx-enrollment-batch-2',
      // Control Plane 필수 필드들
      duration_ms: 5000,
      connections: 'PgConnection@ac889df:PgConnection@139539a4',
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
    t.status === 'active' && t.duration_ms && t.duration_ms >= dashboardConfig.longRunningThresholdMs
  )

  return (
    <div className="min-h-screen relative overflow-hidden">
      {/* Animated Background Gradients */}
      <div className="fixed inset-0 bg-slate-900">
        <div className="absolute inset-0 bg-gradient-to-br from-purple-600/20 via-pink-500/10 to-cyan-500/20"></div>
        <div className="absolute top-0 left-1/4 w-96 h-96 bg-gradient-to-r from-violet-500/30 to-purple-500/30 rounded-full blur-3xl animate-pulse"></div>
        <div className="absolute bottom-0 right-1/4 w-80 h-80 bg-gradient-to-r from-cyan-500/30 to-blue-500/30 rounded-full blur-3xl animate-pulse delay-1000"></div>
        <div className="absolute top-1/2 left-1/2 w-64 h-64 bg-gradient-to-r from-pink-500/20 to-rose-500/20 rounded-full blur-3xl animate-pulse delay-500"></div>
      </div>
      
      <div className="max-w-7xl mx-auto relative z-10 p-6">
        {/* Header */}
        <div className="text-center mb-8">
          <h1 className="text-4xl font-bold text-white mb-2">FlowLight DB Monitor</h1>
          <p className="text-white/60 text-lg mb-4">
            실시간 데이터베이스 성능 인사이트
          </p>
          <div className="glass-morphism inline-block px-6 py-3 rounded-xl">
            <span className={`inline-flex items-center text-sm font-medium ${
              isConnected 
                ? 'text-green-300' 
                : 'text-red-300'
            }`}>
              <div className={`w-3 h-3 rounded-full mr-3 animate-pulse ${
                isConnected ? 'bg-green-400 shadow-neon' : 'bg-red-400 shadow-neon-red'
              }`}></div>
              {isConnected ? '🔗 Connected' : '🔄 Demo Mode'}
            </span>
          </div>
        </div>

        {/* Primary Metrics Cards */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-6 mb-8">
          <MetricCard
            title="QPS"
            value={aggregatedMetrics.qps.toString()}
            unit="queries/sec"
            isAnimated={isConnected}
          />
          <MetricCard
            title="TPS"
            value={(aggregatedMetrics.tps || 0).toString()}
            unit="tx/sec"
            isAnimated={isConnected}
          />
          <MetricCard
            title="Avg Latency"
            value={(aggregatedMetrics.avgLatency || 0).toString()}
            unit="ms"
            isAnimated={isConnected}
          />
          <MetricCard
            title="Active Transactions"
            value={(aggregatedMetrics.transactionCount || 0).toString()}
            unit="transactions"
            isAnimated={isConnected}
          />
          <MetricCard
            title="Error Rate"
            value={(aggregatedMetrics.error_rate || 0).toString()}
            unit="%"
            isAnimated={isConnected}
          />
        </div>

        {/* Live Performance Chart */}
        <div className="mb-12">
          <LivePerformanceChart aggregatedMetrics={aggregatedMetrics} />
        </div>


        {/* Connection Pool Details */}
        <div className="mb-12">
          <div className="glass-morphism rounded-2xl p-8 transition-all duration-300 hover:shadow-2xl hover:shadow-purple-500/10">
            <ConnectionPoolDetail aggregatedMetrics={aggregatedMetrics} />
          </div>
        </div>

        {/* Alert Panels */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8 mb-12">
          <div className="glass-morphism rounded-2xl p-6 transition-all duration-300 hover:shadow-2xl hover:shadow-purple-500/10">
            <DeadlockAlert 
              deadlocks={deadlocks} 
              onResolve={handleResolveDeadlock}
            />
          </div>
          <div className="glass-morphism rounded-2xl p-6 transition-all duration-300 hover:shadow-2xl hover:shadow-purple-500/10">
            <LongRunningTransactionAlert 
              transactions={longRunningTransactions}
              onKillTransaction={handleKillTransaction}
              thresholdSeconds={dashboardConfig.longRunningThresholdMs / 1000}
            />
          </div>
        </div>

        {/* Transaction Timeline */}
        <div className="mb-12">
          <div className="glass-morphism rounded-2xl p-8 transition-all duration-300 hover:shadow-2xl hover:shadow-purple-500/10">
            <TransactionTimeline transactions={transactions} />
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
  const getCardConfig = (title: string) => {
    switch (title) {
      case 'QPS': return {
        icon: Activity,
        color: 'from-blue-500/20 to-cyan-500/20',
        borderColor: 'border-white/20'
      }
      case 'TPS': return {
        icon: Zap,
        color: 'from-purple-500/20 to-pink-500/20',
        borderColor: 'border-white/20'
      }
      case 'Avg Latency': {
        const latency = parseFloat(value)
        return {
          icon: Clock,
          color: latency > 30 ? 'from-orange-500/20 to-yellow-500/20' : 'from-emerald-500/20 to-green-500/20',
          borderColor: latency > 30 ? 'border-orange-500/30' : 'border-white/20'
        }
      }
      case 'Active Transactions': return {
        icon: Database,
        color: 'from-indigo-500/20 to-blue-500/20',
        borderColor: 'border-white/20'
      }
      case 'Error Rate': {
        const errorRate = parseFloat(value)
        return {
          icon: AlertTriangle,
          color: errorRate > 2 ? 'from-red-500/20 to-pink-500/20' : 'from-orange-500/20 to-red-500/20',
          borderColor: errorRate > 2 ? 'border-red-500/30' : 'border-white/20'
        }
      }
      default: return {
        icon: Activity,
        color: 'from-violet-500/20 to-purple-500/20',
        borderColor: 'border-white/20'
      }
    }
  }

  const config = getCardConfig(title)

  return (
    <div className="group transform hover:-translate-y-1 hover:scale-105 transition-all duration-300">
      <div className={`glass-morphism rounded-2xl p-6 bg-gradient-to-br ${config.color} ${config.borderColor} transition-all duration-300 hover:shadow-2xl hover:shadow-purple-500/10`}>
        <div className="flex items-start justify-between mb-4">
          <div className={`p-3 rounded-xl bg-gradient-to-br ${config.color} backdrop-blur-sm`}>
            <config.icon className="w-6 h-6 text-white" />
          </div>
        </div>
        
        <div className="space-y-2">
          <h3 className="text-white/80 text-sm font-medium">{title}</h3>
          <div className="flex items-baseline gap-2">
            <span className="text-3xl font-bold text-white">
              {typeof value === 'string' && !isNaN(parseFloat(value)) 
                ? parseFloat(value).toFixed(title === "Error Rate" ? 2 : 0) 
                : value}
            </span>
            <span className="text-white/60 text-sm">{unit}</span>
          </div>
        </div>
      </div>
    </div>
  )
}