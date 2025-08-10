'use client'

import React, { useEffect, useMemo, useRef } from 'react'
import { motion, useAnimation, AnimatePresence } from 'framer-motion'
import { useRealTimeMetrics } from '@/hooks/useRealTimeMetrics'
import { QueryMetrics } from '@/types/metrics'

// 확장된 QueryMetrics 타입
interface ExtendedQueryMetrics extends QueryMetrics {
  _uniqueId?: string
}
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
    // 모든 이벤트 타입 허용 (필터링 제거)
  })

  // Debug logging
  useEffect(() => {
    console.log('🔍 QueryFlowAnimation Debug:', {
      metricsCount: metrics.length,
      isConnected,
      connectionStatus,
      recentMetrics: metrics.slice(0, 3)
    })
  }, [metrics, isConnected, connectionStatus])

  const controls = useAnimation()

  // Get recent queries for display
  const recentQueries = useMemo(() => {
    const filteredMetrics = metrics
      .filter(m => {
        // 데이터가 있고 쿼리 ID가 있는 메트릭만 필터링
        const hasQueryData = m.data && m.data.query_id
        console.log('🔍 Filtering metric:', { 
          hasData: !!m.data, 
          hasQueryId: !!m.data?.query_id,
          eventType: m.event_type,
          accepted: hasQueryData 
        })
        return hasQueryData
      })
      .slice(0, maxDisplayedQueries)
    
    console.log('🎯 Recent queries after filtering:', filteredMetrics.length, filteredMetrics)
    return filteredMetrics
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
      <div className="mb-6">
        <div className="flex items-center justify-between mb-2">
          <h2 className="text-xl font-bold text-white">Query Performance Pipeline</h2>
          <ConnectionStatusIndicator 
            status={connectionStatus}
            isConnected={isConnected}
          />
        </div>
        <div className="text-sm text-gray-400 space-y-1">
          <p>🚀 <strong>일반 쿼리는 배경으로, 관심있는 느린 쿼리만 강조하여 흐름 시각화</strong></p>
          <p>🎨 색상: 🔵일반 쿼리(배경) 🟠보통 느림(10-50ms) 🟡느림(50ms+) 🔴에러</p>
        </div>
      </div>

      {/* Pipeline Visualization */}
      <div className="pipeline-container mb-8 relative">
        <div className="relative w-full h-32 bg-gray-800 rounded-lg border border-gray-600 overflow-hidden">
          {/* 파이프 배경 */}
          <PipeBackground isConnected={isConnected} aggregatedMetrics={aggregatedMetrics} />
          
          {/* 배경 빠른 파티클들 + 느린 쿼리들 혼합 */}
          <BackgroundParticles isConnected={isConnected} queries={recentQueries} />
        </div>
      </div>

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
              {recentQueries.map((query, index) => (
                <QueryItem key={`query-item-${query.data?.query_id}-${query.timestamp}-${index}`} query={query} />
              ))}
            </AnimatePresence>
          </div>
        )}
      </div>
    </div>
  )
}


// 파이프 배경 렌더링
function PipeBackground({ isConnected, aggregatedMetrics }: { 
  isConnected: boolean
  aggregatedMetrics: any 
}) {
  return (
    <div className="absolute inset-0">
      {/* 메인 파이프 */}
      <div className="absolute top-8 left-0 w-full h-16 bg-gradient-to-r from-blue-900/30 via-purple-900/30 to-green-900/30 border-2 border-gray-600 rounded-lg">
        {/* Connection Pool 상태 기반 병목 지점 */}
        <PoolBottleneck 
          activeConnections={aggregatedMetrics?.active_connections || 0} 
          maxConnections={aggregatedMetrics?.max_connections || 0}
        />
        
      </div>
      
      {/* 커넥션 풀 상태 표시 */}
      <div className="absolute top-1 right-4 text-xs text-gray-400">
        Pool: {aggregatedMetrics?.active_connections || 0}/{aggregatedMetrics?.max_connections || 0}
      </div>
    </div>
  )
}

// 모든 파티클이 실제 쿼리 데이터를 표시
function BackgroundParticles({ isConnected, queries }: { 
  isConnected: boolean, 
  queries: QueryMetrics[] 
}) {
  const counterRef = useRef(0)
  
  // 모든 쿼리를 카테고리별로 분류
  const categorizedQueries = useMemo(() => {
    if (!queries || !Array.isArray(queries)) return { fast: [], medium: [], slow: [], error: [] }
    
    // 중복 제거
    const uniqueQueries = queries.filter((query, index, arr) => {
      const queryKey = `${query.data?.query_id}-${query.timestamp}`
      return arr.findIndex(q => `${q.data?.query_id}-${q.timestamp}` === queryKey) === index
    })
    
    const categorized = {
      fast: [] as ExtendedQueryMetrics[],    // < 10ms
      medium: [] as ExtendedQueryMetrics[],  // 10-50ms  
      slow: [] as ExtendedQueryMetrics[],    // 50ms+
      error: [] as ExtendedQueryMetrics[]    // ERROR
    }
    
    uniqueQueries.forEach((query, index) => {
      const executionTime = query.data?.execution_time_ms || 0
      const isError = query.data?.status === 'ERROR'
      
      counterRef.current += 1
      const extendedQuery: ExtendedQueryMetrics = {
        ...query,
        _uniqueId: `particle-${counterRef.current}-${Date.now()}-${Math.random().toString(36).substr(2, 5)}`
      }
      
      if (isError) {
        categorized.error.push(extendedQuery)
        console.log(`🔴 Error query categorized: ${query.data?.query_id} - ${executionTime}ms`)
      } else if (executionTime >= 50) {
        categorized.slow.push(extendedQuery)
        console.log(`🟡 Slow query categorized: ${query.data?.query_id} - ${executionTime}ms`)
      } else if (executionTime >= 10) {
        categorized.medium.push(extendedQuery)
        console.log(`🟠 Medium query categorized: ${query.data?.query_id} - ${executionTime}ms`)
      } else {
        categorized.fast.push(extendedQuery)
        console.log(`🔵 Fast query categorized: ${query.data?.query_id} - ${executionTime}ms`)
      }
    })
    
    console.log('🎯 Query categorization summary:', {
      fast: categorized.fast.length,
      medium: categorized.medium.length, 
      slow: categorized.slow.length,
      error: categorized.error.length,
      total: uniqueQueries.length
    })
    
    return categorized
  }, [queries])

  // 데이터가 부족할 때 폴백 파티클 생성
  const totalDataParticles = categorizedQueries.fast.length + categorizedQueries.medium.length + 
                            categorizedQueries.slow.length + categorizedQueries.error.length
  const minParticles = 60
  const needsFallback = totalDataParticles < minParticles
  
  return (
    <div className="absolute inset-0 pointer-events-none">
      {isConnected && (
        <AnimatePresence mode="sync">
          {/* 빠른 쿼리들 (배경) */}
          {categorizedQueries.fast.slice(0, 30).map((query, i) => (
            <QueryParticle key={query._uniqueId} query={query} delay={i * 0.5} type="fast" />
          ))}
          
          {/* 보통 느린 쿼리들 */}
          {categorizedQueries.medium.slice(0, 20).map((query, i) => (
            <QueryParticle key={query._uniqueId} query={query} delay={i * 0.3} type="medium" />
          ))}
          
          {/* 느린 쿼리들 - 즉시 표시 */}
          {categorizedQueries.slow.slice(0, 15).map((query, i) => (
            <QueryParticle key={query._uniqueId} query={query} delay={0} type="slow" />
          ))}
          
          {/* 에러 쿼리들 - 즉시 표시 */}
          {categorizedQueries.error.slice(0, 10).map((query, i) => (
            <QueryParticle key={query._uniqueId} query={query} delay={0} type="error" />
          ))}
          
          {/* 데이터가 부족할 때 폴백 파티클들 */}
          {needsFallback && [...Array(Math.min(minParticles - totalDataParticles, 40))].map((_, i) => (
            <FallbackParticle key={`fallback-${i}`} delay={totalDataParticles + i} />
          ))}
        </AnimatePresence>
      )}
    </div>
  )
}

// Connection Pool 상태 기반 병목 표시
function PoolBottleneck({ activeConnections, maxConnections }: { 
  activeConnections: number, 
  maxConnections: number 
}) {
  // Connection Pool 사용률 계산
  const usageRatio = activeConnections / maxConnections
  const availableConnections = maxConnections - activeConnections
  
  // 경고 레벨 결정
  let warningLevel: 'normal' | 'warning' | 'critical' = 'normal'
  let bottleneckColor = 'border-green-500/50 bg-green-900/20'
  let textColor = 'text-green-300'
  let statusText = '정상'
  
  if (availableConnections <= 2) {
    warningLevel = 'critical'
    bottleneckColor = 'border-red-500 bg-red-900/40'
    textColor = 'text-red-200'
    statusText = '위험'
  } else if (availableConnections <= 5) {
    warningLevel = 'warning'
    bottleneckColor = 'border-yellow-500/70 bg-yellow-900/30'
    textColor = 'text-yellow-200'
    statusText = '경고'
  }

  return (
    <motion.div 
      className={`absolute top-2 left-1/2 transform -translate-x-1/2 w-16 h-12 border-2 rounded-sm ${bottleneckColor}`}
      animate={warningLevel === 'critical' ? {
        scale: [1, 1.05, 1],
        borderColor: ['#ef4444', '#dc2626', '#ef4444']
      } : warningLevel === 'warning' ? {
        scale: [1, 1.02, 1]
      } : {}}
      transition={{
        duration: 2,
        repeat: warningLevel !== 'normal' ? Infinity : 0,
        ease: "easeInOut"
      }}
    >
      <div className={`text-xs ${textColor} text-center leading-3 pt-1`}>
        <div className="font-bold">{statusText}</div>
        <div className="text-[10px]">{availableConnections}개 여유</div>
      </div>
    </motion.div>
  )
}

// 통합된 쿼리 파티클 컴포넌트 (모든 타입 처리)
function QueryParticle({ query, delay, type }: { 
  query: ExtendedQueryMetrics, 
  delay: number,
  type: 'fast' | 'medium' | 'slow' | 'error'
}) {
  const [isVisible, setIsVisible] = React.useState(false)
  
  React.useEffect(() => {
    // 더 빠른 시작 시간으로 즉시 가시성 확보
    const randomDelay = Math.random() * 5000 + delay * 500 // 0-5초 랜덤 + 순차 오프셋
    const timer = setTimeout(() => setIsVisible(true), randomDelay)
    return () => clearTimeout(timer)
  }, [delay])
  
  // 단순한 일직선 궤도 (높이만 다름)
  const trajectory = React.useMemo(() => {
    const fixedY = 40 + Math.random() * 30 // 각 파티클마다 고정된 높이 (40-70px)
    
    // 일직선 궤도
    const points = []
    for (let i = 0; i <= 12; i++) {
      const x = -20 + (i * 100) // 100px 간격으로 일정하게
      points.push({ x, y: fixedY }) // Y는 고정
    }
    
    return points
  }, [])
  
  // 쿼리 타입에 따른 파티클 속성
  const particleProps = React.useMemo(() => {
    const executionTime = query.data?.execution_time_ms || 0
    
    const typeConfig = {
      fast: {
        color: `hsl(${190 + Math.random() * 50}, 70%, ${50 + Math.random() * 30}%)`, // 더 밝은 파란색 계열
        size: 6 + Math.random() * 4, // 6-10px (크게 증가)
        opacity: 0.7 + Math.random() * 0.2, // 0.7-0.9 (더 밝게)
        glow: 8,
        blur: 0.5
      },
      medium: {
        color: '#ff9800', // 주황색
        size: 8 + Math.random() * 4, // 8-12px (증가)
        opacity: 0.8 + Math.random() * 0.2, // 0.8-1.0 (더 밝게)
        glow: 10,
        blur: 0.3
      },
      slow: {
        color: '#ffab00', // 노란색
        size: 10 + Math.random() * 4, // 10-14px (크게 증가)
        opacity: 0.9 + Math.random() * 0.1, // 0.9-1.0 (매우 밝게)
        glow: 12,
        blur: 0.2
      },
      error: {
        color: '#ff5252', // 빨간색
        size: 12 + Math.random() * 4, // 12-16px (가장 크게)
        opacity: 1.0, // 1.0 (최대 밝기)
        glow: 15,
        blur: 0.1
      }
    }
    
    return {
      ...typeConfig[type],
      speed: type === 'fast' ? 25 : type === 'medium' ? 35 : type === 'slow' ? 45 : 60, // 타입별 차별화된 속도
      queryId: query.data?.query_id || 'unknown',
      executionTime
    }
  }, [query, type])
  
  if (!isVisible) return null
  
  console.log(`🎯 ${type.toUpperCase()} query particle:`, {
    queryId: particleProps.queryId,
    executionTime: `${particleProps.executionTime}ms`,
    type,
    color: particleProps.color
  })
  
  return (
    <motion.div
      className="absolute rounded-full pointer-events-none"
      data-testid={`${type}-particle`}
      style={{ 
        width: particleProps.size, 
        height: particleProps.size,
        backgroundColor: particleProps.color,
        boxShadow: `0 0 ${particleProps.glow}px ${particleProps.color}`,
        filter: `blur(${particleProps.blur}px)`,
        zIndex: type === 'fast' ? 1 : 2 // 빠른 쿼리는 배경, 나머지는 전경
      }}
      initial={{ 
        x: trajectory[0].x, 
        y: trajectory[0].y, 
        opacity: 0, 
        scale: 0.5 
      }}
      animate={{ 
        x: trajectory.map(p => p.x),
        y: trajectory.map(p => p.y),
        opacity: type === 'fast' ? [0, particleProps.opacity * 0.8, particleProps.opacity, particleProps.opacity, particleProps.opacity, particleProps.opacity * 0.8, 0] :
                 type === 'medium' ? [0, particleProps.opacity * 0.9, particleProps.opacity, particleProps.opacity, particleProps.opacity, particleProps.opacity * 0.9, 0] :
                 [0, particleProps.opacity, 1.0, particleProps.opacity, 1.0, particleProps.opacity, particleProps.opacity * 0.7], // slow/error는 완전히 사라지지 않고 최소 70% 유지
        scale: type === 'fast' ? [0.5, 1, 1, 1, 1, 1, 0.5] :
               type === 'medium' ? [0.5, 1.1, 1, 1.1, 1, 1.1, 0.5] :
               [0.5, 1.2, 1, 1.3, 1, 1.2, 0.5], // slow/error는 더 큰 스케일 변화
        rotate: 0
      }}
      transition={{
        duration: particleProps.speed,
        repeat: Infinity,
        repeatDelay: type === 'fast' ? (2 + Math.random() * 3) : 
                     type === 'medium' ? (1 + Math.random() * 2) :
                     type === 'slow' ? (0.5 + Math.random() * 1) : 
                     (0.2 + Math.random() * 0.5), // 중요한 쿼리일수록 더 자주 반복
        ease: "linear",
        times: [0, 0.15, 0.3, 0.5, 0.7, 0.85, 1]
      }}
    />
  )
}

// 데이터가 부족할 때 사용하는 폴백 파티클 (시각적 효과용)
function FallbackParticle({ delay }: { delay: number }) {
  const [isVisible, setIsVisible] = React.useState(false)
  
  React.useEffect(() => {
    const randomDelay = Math.random() * 30000 + delay * 200
    const timer = setTimeout(() => setIsVisible(true), randomDelay)
    return () => clearTimeout(timer)
  }, [delay])
  
  const trajectory = React.useMemo(() => {
    const fixedY = 40 + Math.random() * 30
    const points = []
    for (let i = 0; i <= 12; i++) {
      const x = -20 + (i * 100)
      points.push({ x, y: fixedY })
    }
    return points
  }, [])
  
  const particleProps = React.useMemo(() => ({
    size: 4 + Math.random() * 3, // 4-7px (더 크게)
    speed: 30,
    opacity: 0.3 + Math.random() * 0.2, // 0.3-0.5 (더 밝게)
    color: `hsl(${200 + Math.random() * 40}, 50%, ${40 + Math.random() * 20}%)` // 더 선명한 파란색
  }), [])
  
  if (!isVisible) return null
  
  return (
    <motion.div
      className="absolute rounded-full pointer-events-none"
      style={{ 
        width: particleProps.size, 
        height: particleProps.size,
        backgroundColor: particleProps.color,
        filter: 'blur(1.5px)',
        zIndex: 0 // 가장 뒤쪽
      }}
      initial={{ 
        x: trajectory[0].x, 
        y: trajectory[0].y, 
        opacity: 0, 
        scale: 0.5 
      }}
      animate={{ 
        x: trajectory.map(p => p.x),
        y: trajectory.map(p => p.y),
        opacity: [0, particleProps.opacity, particleProps.opacity, particleProps.opacity, 0],
        scale: [0.5, 1, 1, 1, 0.5],
        rotate: 0
      }}
      transition={{
        duration: particleProps.speed,
        repeat: Infinity,
        repeatDelay: 5 + Math.random() * 5,
        ease: "linear",
        times: [0, 0.2, 0.5, 0.8, 1]
      }}
    />
  )
}


// 데이터 쿼리 파티클 (지속적으로 생성되는 느린/문제 쿼리 시뮬레이션)
function DataQueryParticle({ delay }: { delay: number }) {
  // 각 파티클마다 완전히 독립적인 생성 시점 설정
  const [isVisible, setIsVisible] = React.useState(false)
  
  React.useEffect(() => {
    // 완전히 랜덤한 시작 시간으로 자연스러운 분산
    const randomDelay = Math.random() * 30000 + delay * 180 // 0-30초 랜덤 + 작은 순차 오프셋
    const timer = setTimeout(() => setIsVisible(true), randomDelay)
    return () => clearTimeout(timer)
  }, [delay])
  
  // 단순한 일직선 궤도 (높이만 다름)
  const trajectory = React.useMemo(() => {
    const fixedY = 40 + Math.random() * 30 // 각 파티클마다 고정된 높이 (40-70px)
    
    // 일직선 궤도
    const points = []
    for (let i = 0; i <= 12; i++) {
      const x = -20 + (i * 100) // 100px 간격으로 일정하게
      points.push({ x, y: fixedY }) // Y는 고정
    }
    
    return points
  }, [])
  
  // 랜덤한 데이터 파티클 특성
  const particleProps = React.useMemo(() => {
    const types = [
      { color: '#ff9800', size: 6 + Math.random() * 3 }, // 주황 (보통 느림)
      { color: '#ffab00', size: 7 + Math.random() * 3 }, // 노랑 (느림)
      { color: '#ff5252', size: 8 + Math.random() * 2 }  // 빨강 (에러)
    ]
    const randomType = types[Math.floor(Math.random() * types.length)]
    
    return {
      ...randomType,
      speed: 30, // 배경 파티클과 동일한 속도
      opacity: 0.8 + Math.random() * 0.2, // 0.8-1.0 (더 밝게)
    }
  }, [])
  
  if (!isVisible) return null
  
  return (
    <motion.div
      className="absolute rounded-full pointer-events-none"
      style={{ 
        width: particleProps.size, 
        height: particleProps.size,
        backgroundColor: particleProps.color,
        boxShadow: `0 0 8px ${particleProps.color}`,
        filter: 'blur(0.5px)',
        zIndex: 2 // 배경 파티클과 섞이도록
      }}
      initial={{ 
        x: trajectory[0].x, 
        y: trajectory[0].y, 
        opacity: 0, 
        scale: 0.5 
      }}
      animate={{ 
        x: trajectory.map(p => p.x),
        y: trajectory.map(p => p.y),
        opacity: [0, particleProps.opacity * 0.8, particleProps.opacity, particleProps.opacity, particleProps.opacity, particleProps.opacity * 0.8, 0], // 단순한 fade in/out
        scale: [0.5, 1, 1, 1, 1, 1, 0.5], // 단순한 scale in/out
        rotate: 0 // 회전 없음
      }}
      transition={{
        duration: particleProps.speed + Math.random() * 10 - 5, // 25-35초로 랜덤화
        repeat: Infinity,
        repeatDelay: 3 + Math.random() * 6, // 3-9초 랜덤 간격
        ease: "linear", // 일정한 속도
        times: [0, 0.15, 0.3, 0.5, 0.7, 0.85, 1] // 단순한 시간 분할
      }}
    />
  )
}


// 느린/문제 쿼리 파티클 (배경 흐름과 자연스럽게 섞임)
function ProblemQueryParticle({ query, delay }: { query: ExtendedQueryMetrics, delay: number }) {
  const executionTime = query.data?.execution_time_ms || 0
  const isError = query.data?.status === 'ERROR'
  
  const particleColor = isError 
    ? '#ff5252' // Red for errors
    : executionTime > 50 
    ? '#ffab00' // Yellow for slow
    : '#ff9800' // Orange for medium

  // 크기는 실행시간에 비례하되, 더 크고 눈에 띄게
  const baseSize = isError ? 8 : executionTime > 50 ? 7 : 6 // 더 크게 만들어서 잘 보이도록
  const size = baseSize + Math.random() * 3 
  
  // 배경 파티클과 같은 충돌 효과 궤도
  const trajectory = React.useMemo(() => {
    const baseY = 40 + Math.random() * 30 // 기본 높이
    
    // 충돌 효과를 시뮬레이션하는 더 많은 지점들
    const points = []
    for (let i = 0; i <= 12; i++) {
      const x = -20 + (i * 100) // 100px 간격으로 더 세밀하게
      let y = baseY + (Math.random() - 0.5) * 15 // 기본 높이 ±7.5px
      
      // 특정 지점에서 "충돌" 효과 (갑작스러운 방향 변화)
      if (i === 3 || i === 7 || i === 10) { // 충돌 지점들
        y = baseY + (Math.random() - 0.5) * 25 // 더 큰 변화
      }
      
      // 경계 체크
      y = Math.max(40, Math.min(70, y))
      points.push({ x, y })
    }
    
    return points
  }, [])
  
  // 배경 파티클과 동일한 속도와 타이밍으로 맞춤
  const speed = 30 // 30초 고정 (배경 파티클과 동일)
  const appearDelay = delay * 500 // 배경 파티클과 동일한 지연시간 패턴

  console.log('🎯 Problem query particle:', {
    queryId: query.data?.query_id,
    executionTime: `${executionTime}ms`,
    isError,
    color: particleColor
  })

  return (
    <motion.div
      className="absolute rounded-full pointer-events-none"
      data-testid="problem-particle"
      style={{
        width: size,
        height: size,
        backgroundColor: particleColor,
        boxShadow: `0 0 8px ${particleColor}`, // 적당한 glow 효과
        filter: 'blur(0.5px)', // 배경 파티클과 유사한 블러
        zIndex: 2 // 배경 파티클과 섞이도록 낮춤
      }}
      initial={{ 
        x: trajectory[0].x, 
        y: trajectory[0].y, 
        opacity: 0, 
        scale: 0.5 
      }}
      animate={{ 
        x: trajectory.map(p => p.x),
        y: trajectory.map(p => p.y),
        opacity: [0, 1, 1, 1.2, 1, 1.1, 0.8, 1.4, 1, 0.9, 1.2, 0.8, 0], // 배경 파티클과 유사한 패턴이지만 더 밝게
        scale: [0.3, 0.7, 1, 1.3, 0.9, 1.1, 0.8, 1.4, 1, 0.9, 1.2, 0.8, 0.2], // 배경 파티클과 유사한 충돌 효과
        rotate: [0, 5, -3, 8, -5, 2, -7, 10, -2, 4, -6, 3, 0] // 배경 파티클과 동일한 회전 패턴
      }}
      exit={{ opacity: 0, scale: 0, transition: { duration: 3 } }} // 더 느린 사라짐
      transition={{
        duration: speed,
        repeat: Infinity,
        repeatDelay: 4, // 4초 고정 간격으로 배경 파티클과 동일
        ease: "linear", // 균등한 속도로 충돌 효과가 더 명확하게
        times: [0, 0.08, 0.15, 0.25, 0.32, 0.4, 0.48, 0.6, 0.68, 0.75, 0.85, 0.92, 1] // 배경 파티클과 동일한 시간 단계
      }}
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