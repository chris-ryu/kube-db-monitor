'use client'

import { useEffect, useMemo } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { clsx } from 'clsx'

export interface TimeSeriesDataPoint {
  timestamp: string
  value: number
}

export interface MetricCardProps {
  title: string
  value: number | string
  previousValue?: number
  icon: string
  change?: number
  changeLabel?: string
  variant?: 'primary' | 'success' | 'warning' | 'error' | 'default'
  isLoading?: boolean
  trendData?: TimeSeriesDataPoint[]
  threshold?: number
  thresholdType?: 'min' | 'max'
  onClick?: () => void
  tooltip?: string
  subtitle?: string
  unit?: string
  format?: 'number' | 'percentage' | 'bytes' | 'duration'
  showComparison?: boolean
  className?: string
}

export function MetricCard({
  title,
  value,
  previousValue,
  icon,
  change,
  changeLabel,
  variant = 'default',
  isLoading = false,
  trendData,
  threshold,
  thresholdType,
  onClick,
  tooltip,
  subtitle,
  unit,
  format = 'number',
  showComparison = false,
  className,
}: MetricCardProps) {
  const formattedValue = formatValue(value, format)
  const formattedChange = useMemo(() => {
    if (showComparison && previousValue !== undefined && typeof value === 'number') {
      const percentChange = ((value - previousValue) / previousValue) * 100
      return {
        value: percentChange,
        label: `${percentChange > 0 ? '+' : ''}${percentChange.toFixed(1)}%`
      }
    }
    return change !== undefined ? { value: change, label: changeLabel || '' } : null
  }, [value, previousValue, change, changeLabel, showComparison])

  const isAlert = useMemo(() => {
    if (threshold === undefined || typeof value !== 'number') return false
    
    return thresholdType === 'max' ? value > threshold : value < threshold
  }, [value, threshold, thresholdType])

  const variantStyles = {
    primary: 'bg-blue-600/10 border-blue-400 text-blue-100',
    success: 'bg-green-600/10 border-green-400 text-green-100',
    warning: 'bg-yellow-600/10 border-yellow-400 text-yellow-100',
    error: 'bg-red-600/10 border-red-400 text-red-100',
    default: 'bg-gray-800/50 border-gray-600 text-gray-100',
  }

  const changeColor = formattedChange
    ? formattedChange.value > 0
      ? 'text-green-400'
      : formattedChange.value < 0
      ? 'text-red-400'
      : 'text-gray-400'
    : 'text-gray-400'

  const hasSignificantChange = Math.abs(formattedChange?.value || 0) > 10

  return (
    <motion.div
      data-testid="metric-card"
      className={clsx(
        'metric-card p-6 rounded-xl border-2 backdrop-blur-sm',
        'transition-all duration-300 cursor-pointer',
        'hover:shadow-lg hover:shadow-blue-500/20',
        variantStyles[variant],
        className
      )}
      whileHover={{ scale: 1.02, y: -2 }}
      whileTap={{ scale: 0.98 }}
      animate={hasSignificantChange ? {
        scale: [1, 1.02, 1],
        transition: { duration: 0.3 }
      } : {}}
      onClick={onClick}
      title={tooltip}
    >
      {/* Header */}
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center space-x-2">
          <span className="text-2xl">{icon}</span>
          <div>
            <h3 className="font-semibold text-white">{title}</h3>
            {subtitle && (
              <p className="text-sm text-gray-400">{subtitle}</p>
            )}
          </div>
        </div>
        
        {isAlert && (
          <motion.div
            data-testid="alert-indicator"
            className="w-3 h-3 bg-red-400 rounded-full"
            animate={{
              scale: [1, 1.2, 1],
              opacity: [1, 0.7, 1],
            }}
            transition={{
              duration: 1,
              repeat: Infinity,
            }}
          />
        )}
      </div>

      {/* Main Content */}
      {isLoading ? (
        <MetricSkeleton />
      ) : (
        <div className="space-y-3">
          {/* Value */}
          <div className="flex items-baseline space-x-2">
            <motion.span
              data-testid="motion-span"
              className="text-3xl font-bold text-white"
              key={formattedValue}
              initial={{ scale: 0.8, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              transition={{ duration: 0.2 }}
            >
              {formattedValue}
            </motion.span>
            {unit && (
              <span className="text-lg text-gray-400">{unit}</span>
            )}
          </div>

          {/* Change Indicator */}
          {formattedChange && (
            <motion.div
              className={clsx('flex items-center space-x-1', changeColor)}
              initial={{ x: -10, opacity: 0 }}
              animate={{ x: 0, opacity: 1 }}
              transition={{ delay: 0.1 }}
            >
              <span className="text-sm font-medium">
                {formattedChange.label}
              </span>
            </motion.div>
          )}

          {/* Trend Graph */}
          {trendData && trendData.length > 0 && (
            <TrendGraph data={trendData} />
          )}
        </div>
      )}
    </motion.div>
  )
}

interface TrendGraphProps {
  data: TimeSeriesDataPoint[]
}

function TrendGraph({ data }: TrendGraphProps) {
  const points = useMemo(() => {
    if (data.length < 2) return ''
    
    const maxValue = Math.max(...data.map(d => d.value))
    const minValue = Math.min(...data.map(d => d.value))
    const range = maxValue - minValue || 1

    const width = 100
    const height = 30
    
    return data
      .map((point, index) => {
        const x = (index / (data.length - 1)) * width
        const y = height - ((point.value - minValue) / range) * height
        return `${index === 0 ? 'M' : 'L'} ${x} ${y}`
      })
      .join(' ')
  }, [data])

  if (!points) return null

  return (
    <div data-testid="trend-graph" className="mt-3">
      <svg width="100" height="30" className="opacity-60">
        <motion.path
          d={points}
          stroke="currentColor"
          strokeWidth="2"
          fill="none"
          initial={{ pathLength: 0 }}
          animate={{ pathLength: 1 }}
          transition={{ duration: 1, ease: "easeInOut" }}
        />
      </svg>
    </div>
  )
}

function MetricSkeleton() {
  return (
    <div data-testid="metric-skeleton" className="space-y-3">
      <motion.div
        className="h-8 bg-gray-700 rounded w-24"
        animate={{ opacity: [0.5, 1, 0.5] }}
        transition={{ duration: 1.5, repeat: Infinity }}
      />
      <motion.div
        className="h-4 bg-gray-700 rounded w-16"
        animate={{ opacity: [0.5, 1, 0.5] }}
        transition={{ duration: 1.5, repeat: Infinity, delay: 0.2 }}
      />
    </div>
  )
}

function formatValue(value: number | string, format: string): string {
  if (typeof value === 'string') return value
  if (isNaN(value) || value === null || value === undefined) return '--'
  
  switch (format) {
    case 'percentage':
      return `${(value * 100).toFixed(2)}%`
    
    case 'bytes':
      const units = ['B', 'KB', 'MB', 'GB', 'TB']
      let size = value
      let unitIndex = 0
      
      while (size >= 1024 && unitIndex < units.length - 1) {
        size /= 1024
        unitIndex++
      }
      
      return `${size.toFixed(2)} ${units[unitIndex]}`
    
    case 'duration':
      const seconds = Math.floor(value / 1000)
      const minutes = Math.floor(seconds / 60)
      const hours = Math.floor(minutes / 60)
      
      if (hours > 0) {
        return `${hours}h ${minutes % 60}m`
      } else if (minutes > 0) {
        return `${minutes}m ${seconds % 60}s`
      } else {
        return `${seconds}s`
      }
    
    case 'number':
    default:
      // Add thousand separators
      if (value >= 1000) {
        return value.toLocaleString()
      } else if (value % 1 !== 0) {
        return value.toFixed(2)
      }
      return value.toString()
  }
}