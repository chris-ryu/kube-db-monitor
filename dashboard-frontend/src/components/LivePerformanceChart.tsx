'use client'

import { useState, useEffect } from 'react'
import { AggregatedMetrics } from '@/types/metrics'
import { LineChart, Line, XAxis, YAxis, ResponsiveContainer, Tooltip } from 'recharts'
import { TrendingUp } from 'lucide-react'

interface PerformanceDataPoint {
  timestamp: number
  connectionPool: number  // percentage
  tps: number
  avgLatency: number
}

interface LivePerformanceChartProps {
  aggregatedMetrics: AggregatedMetrics
  maxDataPoints?: number
}

const CustomTooltip = ({ active, payload, label }: any) => {
  if (active && payload && payload.length) {
    return (
      <div className="glass-morphism rounded-lg p-3 border border-white/20">
        <p className="text-white/80 text-sm mb-1">
          {new Date(label).toLocaleTimeString()}
        </p>
        {payload.map((entry: any, index: number) => (
          <p key={index} className="text-white font-medium text-sm">
            {entry.name}: {entry.value.toFixed(entry.name === "Connection Pool" ? 1 : 0)}
            {entry.name === "Connection Pool" ? "%" : entry.name === "Avg Latency" ? "ms" : ""}
          </p>
        ))}
      </div>
    );
  }
  return null;
};

export function LivePerformanceChart({ aggregatedMetrics, maxDataPoints = 30 }: LivePerformanceChartProps) {
  const [dataPoints, setDataPoints] = useState<PerformanceDataPoint[]>([])

  // Add new data point when metrics update
  useEffect(() => {
    const newDataPoint: PerformanceDataPoint = {
      timestamp: Date.now(),
      connectionPool: (aggregatedMetrics.poolUsageRatio || 0) * 100,
      tps: aggregatedMetrics.tps || 0,
      avgLatency: aggregatedMetrics.avgLatency || 0
    }

    setDataPoints(prev => {
      const updated = [...prev, newDataPoint].slice(-maxDataPoints)
      return updated
    })
  }, [aggregatedMetrics, maxDataPoints])

  return (
    <div className="glass-morphism rounded-2xl p-6 bg-gradient-to-br from-white/5 to-white/10 border border-white/20 transition-all duration-300 hover:shadow-2xl hover:shadow-purple-500/10">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h3 className="text-xl font-bold text-white mb-1">Live Performance Metrics</h3>
          <p className="text-white/60 text-sm">Real-time database performance trends</p>
        </div>
        <div className="p-2 rounded-lg bg-gradient-to-r from-green-500/20 to-emerald-500/20">
          <TrendingUp className="w-5 h-5 text-emerald-400" />
        </div>
      </div>

      <div className="h-80 mb-6">
        {dataPoints.length > 0 ? (
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={dataPoints} margin={{ top: 5, right: 30, left: 20, bottom: 5 }}>
              <XAxis 
                dataKey="timestamp"
                type="number"
                scale="time"
                domain={['dataMin', 'dataMax']}
                tickFormatter={(time) => new Date(time).toLocaleTimeString().slice(0, 5)}
                axisLine={false}
                tickLine={false}
                tick={{ fill: 'rgba(255, 255, 255, 0.6)', fontSize: 12 }}
              />
              <YAxis 
                axisLine={false}
                tickLine={false}
                tick={{ fill: 'rgba(255, 255, 255, 0.6)', fontSize: 12 }}
              />
              <Tooltip content={<CustomTooltip />} />
              
              <Line 
                type="monotone" 
                dataKey="connectionPool" 
                stroke="#06b6d4" 
                strokeWidth={3}
                dot={false}
                name="Connection Pool"
              />
              <Line 
                type="monotone" 
                dataKey="tps" 
                stroke="#8b5cf6" 
                strokeWidth={3}
                dot={false}
                name="TPS"
              />
              <Line 
                type="monotone" 
                dataKey="avgLatency" 
                stroke="#10b981" 
                strokeWidth={3}
                dot={false}
                name="Avg Latency"
              />
            </LineChart>
          </ResponsiveContainer>
        ) : (
          <div className="h-full flex items-center justify-center text-gray-500">
            <div className="text-center">
              <TrendingUp className="w-16 h-16 mb-2 opacity-50 mx-auto" />
              <p>Collecting performance data...</p>
            </div>
          </div>
        )}
      </div>

      {/* Legend */}
      <div className="grid grid-cols-3 gap-4 pt-4 border-t border-white/10">
        <div className="text-center">
          <div className="w-3 h-3 bg-cyan-400 rounded-full mx-auto mb-1 shadow-lg shadow-cyan-400/50"></div>
          <p className="text-white/80 text-xs font-medium">Connection Pool</p>
        </div>
        <div className="text-center">
          <div className="w-3 h-3 bg-purple-400 rounded-full mx-auto mb-1 shadow-lg shadow-purple-400/50"></div>
          <p className="text-white/80 text-xs font-medium">TPS</p>
        </div>
        <div className="text-center">
          <div className="w-3 h-3 bg-emerald-400 rounded-full mx-auto mb-1 shadow-lg shadow-emerald-400/50"></div>
          <p className="text-white/80 text-xs font-medium">Latency</p>
        </div>
      </div>
    </div>
  )
}