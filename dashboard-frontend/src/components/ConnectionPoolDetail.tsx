'use client'

import { useState, useEffect } from 'react'
import { ConnectionPoolDetail as ConnectionPoolDetailType, PoolHealthInfo, PoolHealthStatus, AggregatedMetrics } from '@/types/metrics'
import { Zap, TrendingUp, Rocket, BarChart3, AlertTriangle, Database, Gauge, Target, Users, Clock, Activity, Shield, CheckCircle2, AlertCircle, XCircle } from 'lucide-react'

interface ConnectionPoolDetailProps {
  aggregatedMetrics: AggregatedMetrics
}

export function ConnectionPoolDetail({ aggregatedMetrics }: ConnectionPoolDetailProps) {
  const [poolDetail, setPoolDetail] = useState<ConnectionPoolDetailType | null>(null)
  const [healthInfo, setHealthInfo] = useState<PoolHealthInfo | null>(null)

  useEffect(() => {
    // AggregatedMetrics를 ConnectionPoolDetail로 변환
    if (aggregatedMetrics) {
      const detail: ConnectionPoolDetailType = {
        pool_name: aggregatedMetrics.pool_type || 'HikariCP',
        pool_type: 'HikariCP',
        current_active: aggregatedMetrics.activeConnections || 0,
        current_idle: aggregatedMetrics.idleConnections || 0,
        max_pool_size: aggregatedMetrics.maxConnections || 0,
        peak_active: aggregatedMetrics.peakActiveConnections || 0,
        peak_timestamp: aggregatedMetrics.peakTimestamp ? new Date(aggregatedMetrics.peakTimestamp).toISOString() : new Date().toISOString(),
        health_score: aggregatedMetrics.poolHealthScore || 0,
        requests_per_second: aggregatedMetrics.connectionRequestsPerSecond || 0,
        average_hold_time: aggregatedMetrics.averageHoldTime || 0,
        waiting_threads: aggregatedMetrics.waitingThreads || 0,
        usage_ratio: aggregatedMetrics.poolUsageRatio || 0,
      }
      
      setPoolDetail(detail)
      setHealthInfo(calculateHealthInfo(detail))
    }
  }, [aggregatedMetrics])

  const calculateHealthInfo = (detail: ConnectionPoolDetailType): PoolHealthInfo => {
    const score = detail.health_score
    let status: PoolHealthStatus = 'excellent'
    const issues: string[] = []
    const recommendations: string[] = []

    // score가 0인 경우는 초기 상태 (아직 메트릭 수집 안됨)로 간주
    if (score === 0) {
      status = 'excellent'
      // 초기 상태에서는 이슈나 권장사항을 표시하지 않음
    } else if (score >= 90) {
      status = 'excellent'
    } else if (score >= 75) {
      status = 'good'
    } else if (score >= 60) {
      status = 'warning'
      issues.push('High connection pool usage')
      recommendations.push('Consider increasing max pool size')
    } else {
      status = 'critical'
      issues.push('Connection pool is saturated')
      recommendations.push('Increase pool size or optimize queries immediately')
      recommendations.push('Check for connection leaks')
    }

    // 실제 활동이 있는 상태에서만 이슈 체크
    if (score > 0) {
      if (detail.waiting_threads > 0) {
        issues.push(`${detail.waiting_threads} threads waiting for connection`)
        recommendations.push('Increase pool size or improve query performance')
      }

      if (detail.usage_ratio > 0.8) {
        issues.push('Connection pool usage exceeded 80%')
      }
    }

    return { status, score, issues, recommendations }
  }

  const getHealthColor = (status: PoolHealthStatus) => {
    switch (status) {
      case 'excellent': return 'text-green-400'
      case 'good': return 'text-blue-400'
      case 'warning': return 'text-yellow-400'
      case 'critical': return 'text-red-400'
      default: return 'text-gray-400'
    }
  }

  const getHealthBgColor = (status: PoolHealthStatus) => {
    switch (status) {
      case 'excellent': return 'bg-green-900'
      case 'good': return 'bg-blue-900'
      case 'warning': return 'bg-yellow-900'
      case 'critical': return 'bg-red-900'
      default: return 'bg-gray-900'
    }
  }

  if (!poolDetail || !healthInfo) {
    return (
      <div className="text-center py-12">
        <div className="glass-skeleton h-32 w-full mb-4"></div>
        <h3 className="text-lg font-semibold mb-4 text-white flex items-center justify-center gap-2">
          🔍 Connection Pool Details
        </h3>
        <div className="text-gray-300">Loading data...</div>
      </div>
    )
  }

  return (
    <div className="relative">
      <div className="flex items-center justify-between mb-6">
        <h3 className="text-xl font-bold text-white flex items-center gap-2">
          🔍 Connection Pool Details
        </h3>
        <div className={`glass-card px-4 py-2 text-sm font-medium ${getHealthColor(healthInfo.status)}`}>
          <span className="flex items-center gap-2">
            <div className={`w-2 h-2 rounded-full ${getHealthColor(healthInfo.status)} opacity-75`}></div>
            {healthInfo.status.toUpperCase()} ({healthInfo.score}%)
          </span>
        </div>
      </div>

      {/* 메인 메트릭 그리드 */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
        <div className="glass-dark p-6 rounded-xl glass-hover group">
          <div className="flex items-center justify-between mb-2">
            <div className="text-sm text-gray-300">Current Active</div>
            <Zap className="w-5 h-5 opacity-60 group-hover:opacity-100 transition-opacity" />
          </div>
          <div className="text-3xl font-bold text-green-300 mb-1">{poolDetail.current_active}</div>
          <div className="text-xs text-gray-400">/ {poolDetail.max_pool_size} max</div>
        </div>

        <div className="glass-dark p-6 rounded-xl glass-hover group">
          <div className="flex items-center justify-between mb-2">
            <div className="text-sm text-gray-300">Peak Active</div>
            <TrendingUp className="w-5 h-5 opacity-60 group-hover:opacity-100 transition-opacity" />
          </div>
          <div className="text-3xl font-bold text-blue-300 mb-1">{poolDetail.peak_active}</div>
          <div className="text-xs text-gray-400">
            {poolDetail.peak_timestamp ? new Date(poolDetail.peak_timestamp).toLocaleTimeString() : 'N/A'}
          </div>
        </div>

        <div className="glass-dark p-6 rounded-xl glass-hover group">
          <div className="flex items-center justify-between mb-2">
            <div className="text-sm text-gray-300">Request Rate</div>
            <Rocket className="w-5 h-5 opacity-60 group-hover:opacity-100 transition-opacity" />
          </div>
          <div className="text-3xl font-bold text-yellow-300 mb-1">{poolDetail.requests_per_second}</div>
          <div className="text-xs text-gray-400">req/sec</div>
        </div>

        <div className="glass-dark p-6 rounded-xl glass-hover group">
          <div className="flex items-center justify-between mb-2">
            <div className="text-sm text-gray-300">Avg Hold Time</div>
            <span className="text-lg opacity-60 group-hover:opacity-100 transition-opacity">⏱️</span>
          </div>
          <div className="text-3xl font-bold text-purple-300 mb-1">{poolDetail.average_hold_time.toFixed(1)}</div>
          <div className="text-xs text-gray-400">ms</div>
        </div>
      </div>

      {/* 사용률 차트 */}
      <div className="mb-8">
        <div className="text-lg font-semibold text-white mb-4 flex items-center gap-2">
          <BarChart3 className="w-5 h-5 inline mr-2" />
          Pool Usage
        </div>
        <div className="glass-dark p-4 rounded-xl">
          <div className="w-full bg-black/30 rounded-full h-6 relative overflow-hidden backdrop-blur-sm">
            <div 
              className="bg-gradient-to-r from-green-400 to-cyan-400 h-6 rounded-full transition-all duration-500 ease-out shadow-lg"
              style={{ width: `${Math.min(poolDetail.usage_ratio * 100, 100)}%` }}
            />
            <div className="absolute inset-0 flex items-center justify-center text-sm font-bold text-white drop-shadow-lg">
              {(poolDetail.usage_ratio * 100).toFixed(1)}%
            </div>
            <div className="absolute inset-0 bg-gradient-to-r from-transparent via-white/10 to-transparent animate-shimmer"></div>
          </div>
        </div>
      </div>

      {/* 상태 및 권장사항 */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* 이슈 */}
        {healthInfo.issues.length > 0 && (
          <div className="glass-dark p-6 rounded-xl border border-red-400/30 bg-red-500/10">
            <h4 className="text-lg font-bold text-red-300 mb-4 flex items-center gap-2">
              <AlertTriangle className="w-5 h-5 inline mr-2" />
              Detected Issues
            </h4>
            <ul className="space-y-3">
              {healthInfo.issues.map((issue, index) => (
                <li key={index} className="flex items-start gap-3 group">
                  <span className="text-red-400 text-lg group-hover:scale-110 transition-transform">•</span>
                  <span className="text-red-200 text-sm leading-relaxed">{issue}</span>
                </li>
              ))}
            </ul>
          </div>
        )}

        {/* 권장사항 */}
        {healthInfo.recommendations.length > 0 && (
          <div className="glass-dark p-6 rounded-xl border border-blue-400/30 bg-blue-500/10">
            <h4 className="text-lg font-bold text-blue-300 mb-4 flex items-center gap-2">
              💡 Recommendations
            </h4>
            <ul className="space-y-3">
              {healthInfo.recommendations.map((rec, index) => (
                <li key={index} className="flex items-start gap-3 group">
                  <span className="text-blue-400 text-lg group-hover:scale-110 transition-transform">•</span>
                  <span className="text-blue-200 text-sm leading-relaxed">{rec}</span>
                </li>
              ))}
            </ul>
          </div>
        )}
      </div>

      {/* 추가 정보 */}
      <div className="mt-8 pt-6 border-t border-white/10">
        <div className="glass-dark p-4 rounded-xl">
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 text-sm">
            <div className="flex flex-col gap-1">
              <span className="text-gray-300 font-medium">Pool Type</span>
              <span className="text-green-300 font-bold">{poolDetail.pool_type}</span>
            </div>
            <div className="flex flex-col gap-1">
              <span className="text-gray-300 font-medium">Idle Connections</span>
              <span className="text-cyan-300 font-bold">{poolDetail.current_idle}</span>
            </div>
            <div className="flex flex-col gap-1">
              <span className="text-gray-300 font-medium">Waiting Threads</span>
              <span className={`font-bold ${poolDetail.waiting_threads > 0 ? 'text-red-300' : 'text-green-300'}`}>
                {poolDetail.waiting_threads}
              </span>
            </div>
            <div className="flex flex-col gap-1">
              <span className="text-gray-300 font-medium">Health Score</span>
              <span className={`font-bold ${getHealthColor(healthInfo.status)}`}>
                {healthInfo.score}%
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}