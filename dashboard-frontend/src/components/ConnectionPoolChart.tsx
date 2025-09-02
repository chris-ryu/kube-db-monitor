'use client'

import { useState, useEffect, useRef } from 'react'
import { AggregatedMetrics, PoolUsagePattern } from '@/types/metrics'

interface ConnectionPoolChartProps {
  aggregatedMetrics: AggregatedMetrics
  maxDataPoints?: number
}

export function ConnectionPoolChart({ aggregatedMetrics, maxDataPoints = 30 }: ConnectionPoolChartProps) {
  const [usageHistory, setUsageHistory] = useState<PoolUsagePattern[]>([])
  const canvasRef = useRef<HTMLCanvasElement>(null)

  useEffect(() => {
    // 새로운 데이터 포인트 추가
    const newDataPoint: PoolUsagePattern = {
      timestamp: new Date().toISOString(),
      active_connections: aggregatedMetrics.activeConnections || 0,
      requests_per_second: aggregatedMetrics.connectionRequestsPerSecond || 0,
      response_time_ms: aggregatedMetrics.avgLatency || 0
    }

    setUsageHistory(prev => {
      const updated = [newDataPoint, ...prev].slice(0, maxDataPoints)
      return updated
    })
  }, [aggregatedMetrics, maxDataPoints])

  useEffect(() => {
    drawChart()
  }, [usageHistory])

  const drawChart = () => {
    const canvas = canvasRef.current
    if (!canvas || usageHistory.length === 0) return

    const ctx = canvas.getContext('2d')
    if (!ctx) return

    // 캔버스 크기 설정
    const rect = canvas.getBoundingClientRect()
    canvas.width = rect.width * window.devicePixelRatio
    canvas.height = rect.height * window.devicePixelRatio
    ctx.scale(window.devicePixelRatio, window.devicePixelRatio)

    const width = rect.width
    const height = rect.height
    const padding = 40

    // 배경 초기화
    ctx.fillStyle = '#1f2937' // gray-800
    ctx.fillRect(0, 0, width, height)

    // 데이터 준비
    const data = [...usageHistory].reverse() // 시간 순서대로
    const maxActive = Math.max(...data.map(d => d.active_connections), 1)
    const maxRequests = Math.max(...data.map(d => d.requests_per_second), 1)

    // 그리드 그리기
    ctx.strokeStyle = '#374151' // gray-700
    ctx.lineWidth = 1

    // 수직 그리드
    for (let i = 0; i <= 6; i++) {
      const x = padding + (width - 2 * padding) * i / 6
      ctx.beginPath()
      ctx.moveTo(x, padding)
      ctx.lineTo(x, height - padding)
      ctx.stroke()
    }

    // 수평 그리드
    for (let i = 0; i <= 4; i++) {
      const y = padding + (height - 2 * padding) * i / 4
      ctx.beginPath()
      ctx.moveTo(padding, y)
      ctx.lineTo(width - padding, y)
      ctx.stroke()
    }

    if (data.length < 2) return

    // Active Connections 라인 (녹색)
    ctx.strokeStyle = '#10b981' // green-500
    ctx.lineWidth = 2
    ctx.beginPath()

    data.forEach((point, index) => {
      const x = padding + (width - 2 * padding) * index / (data.length - 1)
      const y = height - padding - (height - 2 * padding) * point.active_connections / maxActive
      
      if (index === 0) {
        ctx.moveTo(x, y)
      } else {
        ctx.lineTo(x, y)
      }
    })
    ctx.stroke()

    // Requests/Second 라인 (파란색)
    ctx.strokeStyle = '#3b82f6' // blue-500
    ctx.lineWidth = 2
    ctx.beginPath()

    data.forEach((point, index) => {
      const x = padding + (width - 2 * padding) * index / (data.length - 1)
      const y = height - padding - (height - 2 * padding) * point.requests_per_second / maxRequests
      
      if (index === 0) {
        ctx.moveTo(x, y)
      } else {
        ctx.lineTo(x, y)
      }
    })
    ctx.stroke()

    // 데이터 포인트 그리기
    data.forEach((point, index) => {
      const x = padding + (width - 2 * padding) * index / (data.length - 1)
      
      // Active connections 포인트
      const yActive = height - padding - (height - 2 * padding) * point.active_connections / maxActive
      ctx.fillStyle = '#10b981'
      ctx.beginPath()
      ctx.arc(x, yActive, 3, 0, 2 * Math.PI)
      ctx.fill()

      // Requests/sec 포인트
      const yRequests = height - padding - (height - 2 * padding) * point.requests_per_second / maxRequests
      ctx.fillStyle = '#3b82f6'
      ctx.beginPath()
      ctx.arc(x, yRequests, 3, 0, 2 * Math.PI)
      ctx.fill()
    })

    // 범례
    ctx.font = '12px sans-serif'
    ctx.fillStyle = '#10b981'
    ctx.fillText('● Active Connections', 10, 20)
    ctx.fillStyle = '#3b82f6'
    ctx.fillText('● Requests/Second', 140, 20)

    // Y축 라벨
    ctx.fillStyle = '#9ca3af'
    ctx.font = '10px sans-serif'
    ctx.textAlign = 'right'
    
    // Active connections 라벨 (좌측)
    for (let i = 0; i <= 4; i++) {
      const value = Math.round(maxActive * (4 - i) / 4)
      const y = padding + (height - 2 * padding) * i / 4
      ctx.fillText(value.toString(), padding - 5, y + 3)
    }

    // Requests/sec 라벨 (우측)
    ctx.textAlign = 'left'
    for (let i = 0; i <= 4; i++) {
      const value = Math.round(maxRequests * (4 - i) / 4)
      const y = padding + (height - 2 * padding) * i / 4
      ctx.fillText(`${value}req/s`, width - padding + 5, y + 3)
    }
  }

  const currentActive = aggregatedMetrics.activeConnections || 0
  const peakActive = aggregatedMetrics.peakActiveConnections || 0
  const requestsPerSec = aggregatedMetrics.connectionRequestsPerSecond || 0
  const healthScore = aggregatedMetrics.poolHealthScore || 0

  const getHealthColor = (score: number) => {
    if (score >= 90) return 'text-green-400'
    if (score >= 75) return 'text-blue-400'
    if (score >= 60) return 'text-yellow-400'
    return 'text-red-400'
  }

  return (
    <div className="bg-gray-800 rounded-lg p-6 border border-green-800">
      <div className="flex items-center justify-between mb-4">
        <h3 className="text-lg font-semibold text-green-400">📈 Connection Pool Real-time Monitoring</h3>
        <div className="flex items-center space-x-4 text-sm">
          <span className="text-gray-400">
            Current: <span className="text-green-400 font-medium">{currentActive}</span>
          </span>
          <span className="text-gray-400">
            Peak: <span className="text-blue-400 font-medium">{peakActive}</span>
          </span>
          <span className="text-gray-400">
            Health: <span className={`font-medium ${getHealthColor(healthScore)}`}>{healthScore}%</span>
          </span>
        </div>
      </div>

      {/* 차트 영역 */}
      <div className="relative">
        <canvas 
          ref={canvasRef} 
          className="w-full h-64 bg-gray-900 rounded"
          style={{ width: '100%', height: '16rem' }}
        />
        
        {usageHistory.length === 0 && (
          <div className="absolute inset-0 flex items-center justify-center text-gray-500">
            Collecting data...
          </div>
        )}
      </div>

      {/* 실시간 통계 */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mt-4">
        <div className="text-center p-3 bg-gray-700 rounded">
          <div className="text-sm text-gray-400">Current Active</div>
          <div className="text-xl font-bold text-green-400">{currentActive}</div>
        </div>
        <div className="text-center p-3 bg-gray-700 rounded">
          <div className="text-sm text-gray-400">Peak Value</div>
          <div className="text-xl font-bold text-blue-400">{peakActive}</div>
        </div>
        <div className="text-center p-3 bg-gray-700 rounded">
          <div className="text-sm text-gray-400">Request Rate</div>
          <div className="text-xl font-bold text-yellow-400">{requestsPerSec}</div>
        </div>
        <div className="text-center p-3 bg-gray-700 rounded">
          <div className="text-sm text-gray-400">Health Score</div>
          <div className={`text-xl font-bold ${getHealthColor(healthScore)}`}>{healthScore}%</div>
        </div>
      </div>

      {/* 트렌드 인사이트 */}
      {usageHistory.length > 5 && (
        <div className="mt-4 p-3 bg-gray-700 rounded">
          <div className="text-sm text-gray-400 mb-2">📊 Trend Analysis</div>
          <div className="text-xs text-gray-300">
            {(() => {
              const recent5 = usageHistory.slice(0, 5)
              const avgRecent = recent5.reduce((sum, p) => sum + p.active_connections, 0) / recent5.length
              const trend = avgRecent > currentActive ? 'Decreasing' : avgRecent < currentActive ? 'Increasing' : 'Stable'
              const trendColor = trend === 'Increasing' ? 'text-red-400' : trend === 'Decreasing' ? 'text-blue-400' : 'text-green-400'
              
              return (
                <span>
                  5-min average: {avgRecent.toFixed(1)}, Trend: <span className={trendColor}>{trend}</span>
                </span>
              )
            })()}
          </div>
        </div>
      )}
    </div>
  )
}