import React, { useState, useMemo } from 'react'
import { QueryMetrics } from '@/types/metrics'

interface NodePodMetricsProps {
  metrics: QueryMetrics[]
  className?: string
}

interface PodMetrics {
  pod_name: string
  namespace: string
  node_name?: string
  qps: number
  avg_latency: number
  error_rate: number
  connection_pool_usage: number
  heap_usage: number
  cpu_usage: number
  query_count: number
  health_status: 'healthy' | 'warning' | 'critical'
}

interface NodeMetrics {
  node_name: string
  pods: PodMetrics[]
  total_qps: number
  avg_latency: number
  total_connections: number
  pod_count: number
}

export function NodePodMetrics({ metrics, className = '' }: NodePodMetricsProps) {
  const [selectedNamespace, setSelectedNamespace] = useState<string>('all')
  const [viewMode, setViewMode] = useState<'node' | 'pod'>('node')
  const [expandedItem, setExpandedItem] = useState<string | null>(null)

  const { podMetrics, nodeMetrics, namespaces } = useMemo(() => {
    const podMap = new Map<string, any>()
    const nodeMap = new Map<string, Set<string>>()
    const namespaceSet = new Set<string>()

    // Process metrics to calculate pod-level aggregations
    metrics.forEach(metric => {
      if (metric.pod_name && metric.namespace) {
        const podKey = `${metric.pod_name}-${metric.namespace}`
        namespaceSet.add(metric.namespace)

        if (!podMap.has(podKey)) {
          podMap.set(podKey, {
            pod_name: metric.pod_name,
            namespace: metric.namespace,
            node_name: extractNodeName(metric.pod_name), // Mock node extraction
            metrics: [],
            queries: []
          })
        }

        const pod = podMap.get(podKey)
        pod.metrics.push(metric.metrics)
        if (metric.event_type === 'query_execution' && metric.data) {
          pod.queries.push(metric.data)
        }

        // Track node-pod relationships
        const nodeName = pod.node_name || 'unknown'
        if (!nodeMap.has(nodeName)) {
          nodeMap.set(nodeName, new Set())
        }
        nodeMap.get(nodeName)!.add(podKey)
      }
    })

    // Calculate pod metrics
    const podMetrics: PodMetrics[] = Array.from(podMap.values()).map(pod => {
      const recentMetrics = pod.metrics.slice(-10) // Last 10 metrics
      const recentQueries = pod.queries.slice(-50) // Last 50 queries

      const qps = recentQueries.length / 60 // Approximate QPS
      const avgLatency = recentQueries.length > 0 
        ? recentQueries.reduce((sum: number, q: any) => sum + (q.execution_time_ms || 0), 0) / recentQueries.length
        : 0
      const errorRate = recentQueries.length > 0
        ? (recentQueries.filter((q: any) => q.status === 'ERROR').length / recentQueries.length) * 100
        : 0

      const latestMetric = recentMetrics[recentMetrics.length - 1]
      const connectionUsage = latestMetric ? 
        ((latestMetric.connection_pool_active || 0) / (latestMetric.connection_pool_max || 1)) * 100 : 0
      const heapUsage = (latestMetric?.heap_usage_ratio || 0) * 100
      const cpuUsage = (latestMetric?.cpu_usage_ratio || 0) * 100

      const healthStatus = getHealthStatus(connectionUsage, heapUsage, cpuUsage, errorRate)

      return {
        pod_name: pod.pod_name,
        namespace: pod.namespace,
        node_name: pod.node_name,
        qps,
        avg_latency: avgLatency,
        error_rate: errorRate,
        connection_pool_usage: connectionUsage,
        heap_usage: heapUsage,
        cpu_usage: cpuUsage,
        query_count: recentQueries.length,
        health_status: healthStatus
      }
    })

    // Calculate node metrics
    const nodeMetrics: NodeMetrics[] = Array.from(nodeMap.entries()).map(([nodeName, podKeys]) => {
      const nodePods = podMetrics.filter(p => podKeys.has(`${p.pod_name}-${p.namespace}`))
      const totalQps = nodePods.reduce((sum, p) => sum + p.qps, 0)
      const avgLatency = nodePods.length > 0
        ? nodePods.reduce((sum, p) => sum + p.avg_latency, 0) / nodePods.length
        : 0
      const totalConnections = nodePods.reduce((sum, p) => 
        sum + (p.connection_pool_usage * 20 / 100), 0) // Approximate active connections

      return {
        node_name: nodeName,
        pods: nodePods,
        total_qps: totalQps,
        avg_latency: avgLatency,
        total_connections: Math.round(totalConnections),
        pod_count: nodePods.length
      }
    })

    const namespaces = ['all', ...Array.from(namespaceSet)]

    return { podMetrics, nodeMetrics, namespaces }
  }, [metrics])

  const filteredPodMetrics = podMetrics.filter(pod => 
    selectedNamespace === 'all' || pod.namespace === selectedNamespace
  )

  const filteredNodeMetrics = nodeMetrics.map(node => ({
    ...node,
    pods: node.pods.filter(pod => selectedNamespace === 'all' || pod.namespace === selectedNamespace)
  })).filter(node => node.pods.length > 0)

  return (
    <div className={`bg-gray-800 rounded-lg p-6 ${className}`}>
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <h3 className="text-lg font-semibold text-gray-300">Node & Pod Metrics</h3>
        
        <div className="flex items-center space-x-4">
          <div className="text-sm text-gray-400">
            <span className="text-blue-400 font-semibold">{nodeMetrics.length}</span> Nodes • 
            <span className="text-green-400 font-semibold ml-1">{podMetrics.length}</span> Pods
          </div>
        </div>
      </div>

      {/* Filters */}
      <div className="flex items-center justify-between mb-6">
        <div className="flex space-x-2">
          {namespaces.map(ns => (
            <button
              key={ns}
              onClick={() => setSelectedNamespace(ns)}
              className={`px-3 py-1 rounded text-sm transition-colors ${
                selectedNamespace === ns
                  ? 'bg-blue-600 text-white'
                  : 'bg-gray-700 text-gray-300 hover:bg-gray-600'
              }`}
            >
              {ns}
            </button>
          ))}
        </div>

        <div className="flex space-x-2">
          <button
            onClick={() => setViewMode('node')}
            className={`px-3 py-1 rounded text-sm transition-colors ${
              viewMode === 'node'
                ? 'bg-green-600 text-white'
                : 'bg-gray-700 text-gray-300 hover:bg-gray-600'
            }`}
          >
            Node View
          </button>
          <button
            onClick={() => setViewMode('pod')}
            className={`px-3 py-1 rounded text-sm transition-colors ${
              viewMode === 'pod'
                ? 'bg-green-600 text-white'
                : 'bg-gray-700 text-gray-300 hover:bg-gray-600'
            }`}
          >
            Pod View
          </button>
        </div>
      </div>

      {/* Content */}
      {viewMode === 'node' ? (
        <div className="space-y-4">
          {filteredNodeMetrics.map(node => (
            <NodeCard
              key={node.node_name}
              node={node}
              isExpanded={expandedItem === node.node_name}
              onToggleExpand={() => setExpandedItem(
                expandedItem === node.node_name ? null : node.node_name
              )}
            />
          ))}
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
          {filteredPodMetrics.map(pod => (
            <PodCard
              key={`${pod.pod_name}-${pod.namespace}`}
              pod={pod}
              isExpanded={expandedItem === `${pod.pod_name}-${pod.namespace}`}
              onToggleExpand={() => setExpandedItem(
                expandedItem === `${pod.pod_name}-${pod.namespace}` 
                  ? null 
                  : `${pod.pod_name}-${pod.namespace}`
              )}
            />
          ))}
        </div>
      )}
    </div>
  )
}

function NodeCard({ node, isExpanded, onToggleExpand }: {
  node: NodeMetrics
  isExpanded: boolean
  onToggleExpand: () => void
}) {
  const getNodeHealth = () => {
    const criticalPods = node.pods.filter(p => p.health_status === 'critical').length
    const warningPods = node.pods.filter(p => p.health_status === 'warning').length
    
    if (criticalPods > 0) return { status: 'critical', icon: '🔴' }
    if (warningPods > 0) return { status: 'warning', icon: '🟡' }
    return { status: 'healthy', icon: '🟢' }
  }

  const health = getNodeHealth()

  return (
    <div className="border border-gray-700 rounded-lg p-4 cursor-pointer hover:bg-gray-900/50"
         onClick={onToggleExpand}>
      <div className="flex items-center justify-between">
        <div className="flex items-center space-x-3">
          <span className="text-lg">{health.icon}</span>
          <div>
            <h4 className="text-gray-200 font-medium">{node.node_name}</h4>
            <p className="text-sm text-gray-400">{node.pod_count} pods</p>
          </div>
        </div>
        
        <div className="text-right text-sm">
          <div className="text-gray-300">
            {node.total_qps.toFixed(1)} QPS • {node.total_connections} connections
          </div>
          <div className="text-gray-500">
            Avg: {node.avg_latency.toFixed(0)}ms
          </div>
        </div>
      </div>

      {isExpanded && (
        <div className="mt-4 pt-4 border-t border-gray-700">
          <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
            {node.pods.map(pod => (
              <PodCard
                key={`${pod.pod_name}-${pod.namespace}`}
                pod={pod}
                isExpanded={false}
                onToggleExpand={() => {}}
                compact
              />
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

function PodCard({ pod, isExpanded, onToggleExpand, compact = false }: {
  pod: PodMetrics
  isExpanded: boolean
  onToggleExpand: () => void
  compact?: boolean
}) {
  const getHealthIcon = (status: string) => {
    switch (status) {
      case 'healthy': return '🟢'
      case 'warning': return '🟡'
      case 'critical': return '🔴'
      default: return '⚪'
    }
  }

  const getBorderColor = (status: string) => {
    switch (status) {
      case 'healthy': return 'border-green-500'
      case 'warning': return 'border-yellow-500'
      case 'critical': return 'border-red-500'
      default: return 'border-gray-600'
    }
  }

  if (compact) {
    return (
      <div className={`border ${getBorderColor(pod.health_status)} rounded p-3 bg-gray-900/30`}>
        <div className="flex items-center justify-between mb-2">
          <span className="text-sm font-medium text-gray-200">{pod.pod_name}</span>
          <span className="text-lg">{getHealthIcon(pod.health_status)}</span>
        </div>
        <div className="text-xs text-gray-400 space-y-1">
          <div>QPS: {pod.qps.toFixed(1)}</div>
          <div>Latency: {pod.avg_latency.toFixed(0)}ms</div>
          <div>Pool: {pod.connection_pool_usage.toFixed(0)}%</div>
        </div>
      </div>
    )
  }

  return (
    <div 
      className={`border ${getBorderColor(pod.health_status)} rounded-lg p-4 cursor-pointer hover:bg-gray-900/50`}
      onClick={onToggleExpand}
      data-testid={`pod-${pod.pod_name}`}
    >
      <div className="flex items-center justify-between mb-3">
        <div className="flex items-center space-x-2">
          <span className="text-lg">{getHealthIcon(pod.health_status)}</span>
          <div>
            <h4 className="text-gray-200 font-medium">{pod.pod_name}</h4>
            <p className="text-xs text-gray-500">{pod.namespace}</p>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-3 text-sm">
        <div>
          <p className="text-gray-400">QPS</p>
          <p className="text-gray-200 font-semibold">{pod.qps.toFixed(1)}</p>
        </div>
        <div>
          <p className="text-gray-400">Avg Latency</p>
          <p className="text-gray-200 font-semibold">{pod.avg_latency.toFixed(0)}ms</p>
        </div>
        <div>
          <p className="text-gray-400">Pool Usage</p>
          <p className="text-gray-200 font-semibold">{pod.connection_pool_usage.toFixed(0)}%</p>
        </div>
        <div>
          <p className="text-gray-400">Error Rate</p>
          <p className={`font-semibold ${pod.error_rate > 5 ? 'text-red-400' : 'text-gray-200'}`}>
            {pod.error_rate.toFixed(1)}%
          </p>
        </div>
      </div>

      {isExpanded && (
        <div className="mt-4 pt-4 border-t border-gray-700 space-y-2">
          <div className="text-sm">
            <p className="text-gray-400">Connection Pool: <span className="text-gray-200">{pod.connection_pool_usage.toFixed(0)}%</span></p>
            <p className="text-gray-400">Heap Usage: <span className="text-gray-200">{pod.heap_usage.toFixed(0)}%</span></p>
            <p className="text-gray-400">CPU Usage: <span className="text-gray-200">{pod.cpu_usage.toFixed(0)}%</span></p>
            <p className="text-gray-400">Query Count: <span className="text-gray-200">{pod.query_count}</span></p>
          </div>
        </div>
      )}
    </div>
  )
}

function extractNodeName(podName: string): string {
  // Mock node extraction - in real implementation, this would come from Kubernetes API
  const hash = podName.split('').reduce((a, b) => {
    a = ((a << 5) - a) + b.charCodeAt(0)
    return a & a
  }, 0)
  return `node-${Math.abs(hash) % 3 + 1}`
}

function getHealthStatus(connectionUsage: number, heapUsage: number, cpuUsage: number, errorRate: number): 'healthy' | 'warning' | 'critical' {
  if (errorRate > 10 || connectionUsage > 90 || heapUsage > 90 || cpuUsage > 90) {
    return 'critical'
  }
  if (errorRate > 5 || connectionUsage > 70 || heapUsage > 70 || cpuUsage > 70) {
    return 'warning'
  }
  return 'healthy'
}