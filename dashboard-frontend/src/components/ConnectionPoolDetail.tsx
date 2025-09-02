'use client'

import { useState, useEffect } from 'react'
import { ConnectionPoolDetail as ConnectionPoolDetailType, PoolHealthInfo, PoolHealthStatus, AggregatedMetrics } from '@/types/metrics'

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
      <div className="bg-gray-800 rounded-lg p-6 border border-green-800">
        <h3 className="text-lg font-semibold mb-4 text-green-400">🔍 Connection Pool Details</h3>
        <div className="text-center text-gray-500">Loading data...</div>
      </div>
    )
  }

  return (
    <div className="bg-gray-800 rounded-lg p-6 border border-green-800">
      <div className="flex items-center justify-between mb-6">
        <h3 className="text-lg font-semibold text-green-400">
          🔍 Connection Pool Details
        </h3>
        <div className={`px-3 py-1 rounded-full text-sm font-medium ${getHealthColor(healthInfo.status)} ${getHealthBgColor(healthInfo.status)}`}>
          {healthInfo.status.toUpperCase()} ({healthInfo.score}%)
        </div>
      </div>

      {/* 메인 메트릭 그리드 */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        <div className="bg-gray-700 p-4 rounded-lg">
          <div className="text-sm text-gray-400 mb-1">Current Active</div>
          <div className="text-2xl font-bold text-green-400">{poolDetail.current_active}</div>
          <div className="text-xs text-gray-500">/ {poolDetail.max_pool_size} max</div>
        </div>

        <div className="bg-gray-700 p-4 rounded-lg">
          <div className="text-sm text-gray-400 mb-1">Peak Active</div>
          <div className="text-2xl font-bold text-blue-400">{poolDetail.peak_active}</div>
          <div className="text-xs text-gray-500">
            {poolDetail.peak_timestamp ? new Date(poolDetail.peak_timestamp).toLocaleTimeString() : 'N/A'}
          </div>
        </div>

        <div className="bg-gray-700 p-4 rounded-lg">
          <div className="text-sm text-gray-400 mb-1">Request Rate</div>
          <div className="text-2xl font-bold text-yellow-400">{poolDetail.requests_per_second}</div>
          <div className="text-xs text-gray-500">req/sec</div>
        </div>

        <div className="bg-gray-700 p-4 rounded-lg">
          <div className="text-sm text-gray-400 mb-1">Avg Hold Time</div>
          <div className="text-2xl font-bold text-purple-400">{poolDetail.average_hold_time.toFixed(1)}</div>
          <div className="text-xs text-gray-500">ms</div>
        </div>
      </div>

      {/* 사용률 차트 */}
      <div className="mb-6">
        <div className="text-sm text-gray-400 mb-2">Pool Usage</div>
        <div className="w-full bg-gray-700 rounded-full h-4 relative">
          <div 
            className="bg-gradient-to-r from-green-500 to-blue-500 h-4 rounded-full transition-all duration-300"
            style={{ width: `${Math.min(poolDetail.usage_ratio * 100, 100)}%` }}
          />
          <div className="absolute inset-0 flex items-center justify-center text-xs font-medium text-white">
            {(poolDetail.usage_ratio * 100).toFixed(1)}%
          </div>
        </div>
      </div>

      {/* 상태 및 권장사항 */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {/* 이슈 */}
        {healthInfo.issues.length > 0 && (
          <div className="bg-red-900 bg-opacity-30 p-4 rounded-lg border border-red-800">
            <h4 className="text-sm font-semibold text-red-400 mb-2">⚠️ Detected Issues</h4>
            <ul className="text-sm text-red-300 space-y-1">
              {healthInfo.issues.map((issue, index) => (
                <li key={index} className="flex items-start">
                  <span className="text-red-500 mr-2">•</span>
                  {issue}
                </li>
              ))}
            </ul>
          </div>
        )}

        {/* 권장사항 */}
        {healthInfo.recommendations.length > 0 && (
          <div className="bg-blue-900 bg-opacity-30 p-4 rounded-lg border border-blue-800">
            <h4 className="text-sm font-semibold text-blue-400 mb-2">💡 Recommendations</h4>
            <ul className="text-sm text-blue-300 space-y-1">
              {healthInfo.recommendations.map((rec, index) => (
                <li key={index} className="flex items-start">
                  <span className="text-blue-500 mr-2">•</span>
                  {rec}
                </li>
              ))}
            </ul>
          </div>
        )}
      </div>

      {/* 추가 정보 */}
      <div className="mt-6 pt-4 border-t border-gray-700">
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
          <div>
            <span className="text-gray-400">Pool Type:</span>
            <span className="ml-2 text-green-400">{poolDetail.pool_type}</span>
          </div>
          <div>
            <span className="text-gray-400">Idle Connections:</span>
            <span className="ml-2 text-green-400">{poolDetail.current_idle}</span>
          </div>
          <div>
            <span className="text-gray-400">Waiting Threads:</span>
            <span className={`ml-2 ${poolDetail.waiting_threads > 0 ? 'text-red-400' : 'text-green-400'}`}>
              {poolDetail.waiting_threads}
            </span>
          </div>
          <div>
            <span className="text-gray-400">Health Score:</span>
            <span className={`ml-2 ${getHealthColor(healthInfo.status)}`}>
              {healthInfo.score}%
            </span>
          </div>
        </div>
      </div>
    </div>
  )
}