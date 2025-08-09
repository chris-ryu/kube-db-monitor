'use client'

import { useEffect, useMemo } from 'react'
import { motion, useAnimation, AnimatePresence } from 'framer-motion'
import { useRealTimeMetrics } from '@/hooks/useRealTimeMetrics'
import { QueryMetrics } from '@/types/metrics'
import { clsx } from 'clsx'

interface QueryFlowAnimationProps {
  className?: string
  maxDisplayedQueries?: number
}

export function QueryFlowAnimation({ 
  className, 
  maxDisplayedQueries = 5 
}: QueryFlowAnimationProps) {
  const { metrics, isConnected, connectionStatus, aggregatedMetrics } = useRealTimeMetrics({
    eventTypes: ['query_execution', 'query_error', 'slow_query']
  })

  const controls = useAnimation()

  // Get recent queries for display
  const recentQueries = useMemo(() => {
    return metrics
      .filter(m => m.data?.query_id)
      .slice(0, maxDisplayedQueries)
  }, [metrics, maxDisplayedQueries])

  // Animate when new queries arrive
  useEffect(() => {
    if (recentQueries.length > 0) {
      controls.start({
        scale: [1, 1.02, 1],
        transition: { duration: 0.3 }
      })
    }
  }, [recentQueries.length, controls])

  return (
    <div 
      className={clsx('query-flow-animation', className)}
      data-testid="query-flow-container"
    >
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <h2 className="text-xl font-bold text-white">Real-time Query Flow</h2>
        <ConnectionStatusIndicator 
          status={connectionStatus}
          isConnected={isConnected}
        />
      </div>

      {/* Flow Visualization */}
      <motion.div 
        className="flow-diagram mb-8"
        animate={controls}
      >
        <div className="flex items-center justify-between">
          {/* App Node */}
          <FlowNode
            testId="app-node"
            icon="🔄"
            label="App"
            className="bg-blue-600/20 border-blue-400"
          />

          {/* Flow Arrow */}
          <FlowArrow label="SQL" />

          {/* Pool Node */}
          <FlowNode
            testId="pool-node"
            icon={`💾 ${aggregatedMetrics.active_connections}/${aggregatedMetrics.max_connections}`}
            label="Pool"
            className="bg-purple-600/20 border-purple-400"
          />

          {/* Flow Arrow */}
          <FlowArrow label="Query" />

          {/* DB Node */}
          <FlowNode
            testId="db-node"
            icon="🗄️"
            label="DB"
            className="bg-green-600/20 border-green-400"
          />

          {/* Flow Arrow */}
          <FlowArrow label="Result" />

          {/* Result Node */}
          <FlowNode
            testId="result-node"
            icon="📊"
            label="App"
            className="bg-blue-600/20 border-blue-400"
          />
        </div>

        {/* Query Particles */}
        <QueryParticles queries={recentQueries} />
      </motion.div>

      {/* Active Queries List */}
      <div className="active-queries">
        <h3 className="text-lg font-semibold text-white mb-4">실행중인 쿼리 애니메이션</h3>
        
        {recentQueries.length === 0 ? (
          <div className="text-gray-400 text-center py-8">
            쿼리 대기 중...
          </div>
        ) : (
          <div className="space-y-2">
            <AnimatePresence>
              {recentQueries.map((query) => (
                <QueryItem key={query.data?.query_id} query={query} />
              ))}
            </AnimatePresence>
          </div>
        )}
      </div>
    </div>
  )
}

interface FlowNodeProps {
  testId: string
  icon: string
  label: string
  className?: string
}

function FlowNode({ testId, icon, label, className }: FlowNodeProps) {
  return (
    <motion.div
      data-testid={testId}
      className={clsx(
        'flex flex-col items-center p-4 rounded-lg border-2',
        'min-w-[80px] min-h-[80px] justify-center',
        className
      )}
      whileHover={{ scale: 1.05 }}
      whileTap={{ scale: 0.95 }}
    >
      <div className="text-2xl mb-1">{icon}</div>
      <div className="text-sm text-gray-300">{label}</div>
    </motion.div>
  )
}

interface FlowArrowProps {
  label: string
}

function FlowArrow({ label }: FlowArrowProps) {
  return (
    <div className="flex flex-col items-center">
      <motion.svg
        width="60"
        height="20"
        viewBox="0 0 60 20"
        className="text-gray-400"
        animate={{ 
          opacity: [0.5, 1, 0.5],
          scale: [1, 1.1, 1]
        }}
        transition={{ 
          duration: 2,
          repeat: Infinity,
          ease: "easeInOut"
        }}
      >
        <motion.path
          d="M10 10 L40 10 M35 5 L40 10 L35 15"
          stroke="currentColor"
          strokeWidth="2"
          fill="none"
          data-testid="motion-path"
        />
      </motion.svg>
      <div className="text-xs text-gray-500 mt-1">{label}</div>
    </div>
  )
}

interface QueryParticlesProps {
  queries: QueryMetrics[]
}

function QueryParticles({ queries }: QueryParticlesProps) {
  const activeQueries = queries.filter(q => {
    const queryTime = new Date(q.timestamp)
    const now = new Date()
    return now.getTime() - queryTime.getTime() < 5000 // Last 5 seconds
  })

  return (
    <div className="absolute inset-0 pointer-events-none">
      <AnimatePresence>
        {activeQueries.map((query, index) => (
          <QueryParticle
            key={`${query.data?.query_id}-${index}`}
            query={query}
            delay={index * 0.1}
          />
        ))}
      </AnimatePresence>
    </div>
  )
}

interface QueryParticleProps {
  query: QueryMetrics
  delay: number
}

function QueryParticle({ query, delay }: QueryParticleProps) {
  const executionTime = query.data?.execution_time_ms || 0
  const isError = query.data?.status === 'ERROR'
  
  const particleColor = isError 
    ? '#ff5252' // Red for errors
    : executionTime > 50 
    ? '#ffab00' // Yellow for slow
    : executionTime > 10
    ? '#ff9800' // Orange for medium  
    : '#00ff88' // Green for fast

  return (
    <motion.div
      className="absolute w-3 h-3 rounded-full z-10"
      data-testid="query-particle"
      initial={{ x: 0, y: 10, opacity: 0, scale: 0.5 }}
      animate={{ 
        x: [0, 150, 300, 450, 600],
        y: [10, 8, 10, 12, 10],
        opacity: [0, 1, 1, 1, 0],
        scale: [0.5, 1, 1, 1, 0.5]
      }}
      exit={{ opacity: 0, scale: 0 }}
      transition={{
        duration: executionTime / 100, // Animation speed based on execution time
        delay,
        ease: "easeInOut"
      }}
      style={{ backgroundColor: particleColor }}
    />
  )
}

interface QueryItemProps {
  query: QueryMetrics
}

function QueryItem({ query }: QueryItemProps) {
  const { data } = query
  if (!data) return null

  const executionTime = data.execution_time_ms || 0
  const isError = data.status === 'ERROR'
  
  const performanceClass = isError
    ? 'query-error'
    : executionTime > 50
    ? 'query-slow'
    : executionTime > 10
    ? 'query-medium'
    : 'query-fast'

  const performanceColor = isError
    ? 'border-red-500 bg-red-500/10'
    : executionTime > 50
    ? 'border-red-400 bg-red-400/10'
    : executionTime > 10
    ? 'border-yellow-400 bg-yellow-400/10'
    : 'border-green-400 bg-green-400/10'

  return (
    <motion.div
      data-testid={`query-${data.query_id}`}
      className={clsx(
        'flex items-center justify-between p-3 rounded-lg border',
        'text-sm',
        performanceClass,
        performanceColor
      )}
      initial={{ x: -300, opacity: 0 }}
      animate={{ x: 0, opacity: 1 }}
      exit={{ x: 300, opacity: 0 }}
      transition={{ duration: 0.3, ease: "easeOut" }}
    >
      <div className="flex-1 min-w-0">
        <div className="text-white font-medium truncate">
          {data.sql_pattern || 'Unknown query'}
        </div>
      </div>
      
      <div className="flex items-center space-x-2 ml-4">
        {isError ? (
          <span className="text-red-400 font-medium">❌ Error</span>
        ) : (
          <span className="text-gray-300 font-medium">
            ⏱️ {executionTime}ms
          </span>
        )}
      </div>
    </motion.div>
  )
}

interface ConnectionStatusIndicatorProps {
  status: 'connecting' | 'connected' | 'disconnected' | 'error'
  isConnected: boolean
}

function ConnectionStatusIndicator({ status, isConnected }: ConnectionStatusIndicatorProps) {
  const statusConfig = {
    connecting: { color: 'text-yellow-400', icon: '🔄', text: '연결 중...' },
    connected: { color: 'text-green-400', icon: '🟢', text: '연결됨' },
    disconnected: { color: 'text-gray-400', icon: '⚪', text: '연결 끊김' },
    error: { color: 'text-red-400', icon: '🔴', text: '연결 오류' },
  }

  const config = statusConfig[status]

  return (
    <div 
      className={clsx('flex items-center space-x-2', config.color)}
      data-testid="connection-status"
    >
      <motion.span
        animate={status === 'connecting' ? { rotate: 360 } : {}}
        transition={status === 'connecting' ? { 
          duration: 1, 
          repeat: Infinity, 
          ease: "linear" 
        } : {}}
      >
        {config.icon}
      </motion.span>
      <span className="text-sm font-medium">{config.text}</span>
    </div>
  )
}