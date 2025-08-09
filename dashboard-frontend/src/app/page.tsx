'use client'

import { useState, useEffect } from 'react'

export default function Dashboard() {
  const { metrics, isConnected, aggregatedMetrics } = useRealTimeMetrics()

  return (
    <div className="min-h-screen bg-gradient-to-br from-gray-900 via-gray-800 to-gray-900 p-6">
      {/* Header */}
      <div className="mb-8">
        <h1 className="text-4xl font-bold text-white mb-2">
          KubeDB Monitor Dashboard
        </h1>
        <p className="text-gray-400">
          Real-time database performance monitoring
        </p>
      </div>

      {/* Main Metrics Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
        <MetricCard
          title="QPS"
          icon="🔥"
          value={aggregatedMetrics.qps}
          change={12}
          changeLabel="↗️ +12%"
          variant="primary"
          unit="req/s"
          tooltip="Queries executed per second"
        />
        
        <MetricCard
          title="Avg Latency"
          icon="⚡"
          value={aggregatedMetrics.avg_latency}
          change={-5}
          changeLabel="↘️ -5ms"
          variant="success"
          unit="ms"
          tooltip="Average query execution time"
        />
        
        <MetricCard
          title="Active Conn"
          icon="🔗"
          value={`${aggregatedMetrics.active_connections}/${aggregatedMetrics.max_connections}`}
          change={2}
          changeLabel="📈 Graph"
          variant="warning"
          tooltip="Active database connections"
        />
        
        <MetricCard
          title="Error Rate"
          icon="❌"
          value={aggregatedMetrics.error_rate}
          change={-0.2}
          changeLabel="↘️ -0.2%"
          variant="success"
          format="percentage"
          threshold={5}
          thresholdType="max"
          tooltip="Query error percentage"
        />
      </div>

      {/* Query Flow Animation */}
      <div className="mb-8">
        <QueryFlowAnimation className="bg-gray-800/30 backdrop-blur-sm rounded-2xl p-6" />
      </div>

      {/* Additional Metrics */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        <MetricCard
          title="Heap Usage"
          icon="💾"
          value={aggregatedMetrics.heap_usage_ratio}
          variant="primary"
          format="percentage"
          threshold={0.8}
          thresholdType="max"
        />
        
        <MetricCard
          title="CPU Usage"
          icon="🖥️"
          value={aggregatedMetrics.cpu_usage_ratio}
          variant="warning"
          format="percentage"
          threshold={0.9}
          thresholdType="max"
        />
        
        <MetricCard
          title="Total Queries"
          icon="📊"
          value={metrics.length}
          variant="default"
        />
      </div>
    </div>
  )
}