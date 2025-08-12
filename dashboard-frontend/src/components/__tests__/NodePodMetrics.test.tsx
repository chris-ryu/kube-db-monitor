import React from 'react'
import { render, screen, fireEvent } from '@testing-library/react'
import { NodePodMetrics } from '../NodePodMetrics'

describe('NodePodMetrics', () => {
  const mockMetrics = [
    {
      timestamp: '2025-08-11T14:35:00Z',
      pod_name: 'app-pod-1',
      namespace: 'production',
      node_name: 'node-1',
      event_type: 'query_execution',
      data: {
        query_id: 'q-1',
        sql_type: 'SELECT',
        execution_time_ms: 150,
        status: 'SUCCESS'
      },
      metrics: {
        connection_pool_active: 5,
        connection_pool_max: 20,
        heap_usage_ratio: 0.6,
        cpu_usage_ratio: 0.4
      }
    },
    {
      timestamp: '2025-08-11T14:35:01Z',
      pod_name: 'app-pod-2',
      namespace: 'production',
      node_name: 'node-1',
      event_type: 'query_execution',
      data: {
        query_id: 'q-2',
        sql_type: 'UPDATE',
        execution_time_ms: 250,
        status: 'SUCCESS'
      },
      metrics: {
        connection_pool_active: 8,
        connection_pool_max: 20,
        heap_usage_ratio: 0.8,
        cpu_usage_ratio: 0.7
      }
    },
    {
      timestamp: '2025-08-11T14:35:02Z',
      pod_name: 'app-pod-3',
      namespace: 'staging',
      node_name: 'node-2',
      event_type: 'query_execution',
      data: {
        query_id: 'q-3',
        sql_type: 'INSERT',
        execution_time_ms: 80,
        status: 'SUCCESS'
      },
      metrics: {
        connection_pool_active: 2,
        connection_pool_max: 10,
        heap_usage_ratio: 0.3,
        cpu_usage_ratio: 0.2
      }
    }
  ]

  it('should render node and pod overview', () => {
    render(<NodePodMetrics metrics={mockMetrics} />)
    
    expect(screen.getByText('Node & Pod Metrics')).toBeInTheDocument()
    
    // Should show node counts
    expect(screen.getByText(/2 Nodes/)).toBeInTheDocument()
    expect(screen.getByText(/3 Pods/)).toBeInTheDocument()
  })

  it('should show metrics by node', () => {
    render(<NodePodMetrics metrics={mockMetrics} />)
    
    expect(screen.getByText('node-1')).toBeInTheDocument()
    expect(screen.getByText('node-2')).toBeInTheDocument()
    
    // node-1 should show 2 pods
    expect(screen.getByText(/2 pods/)).toBeInTheDocument()
    // node-2 should show 1 pod
    expect(screen.getByText(/1 pods/)).toBeInTheDocument()
  })

  it('should filter by namespace', () => {
    render(<NodePodMetrics metrics={mockMetrics} />)
    
    // Should show namespace filter buttons
    expect(screen.getByText('All')).toBeInTheDocument()
    expect(screen.getByText('production')).toBeInTheDocument()
    expect(screen.getByText('staging')).toBeInTheDocument()
    
    // Click on production filter
    fireEvent.click(screen.getByText('production'))
    
    // Should only show production pods
    expect(screen.getByText('app-pod-1')).toBeInTheDocument()
    expect(screen.getByText('app-pod-2')).toBeInTheDocument()
    expect(screen.queryByText('app-pod-3')).not.toBeInTheDocument()
  })

  it('should show pod performance metrics', () => {
    render(<NodePodMetrics metrics={mockMetrics} />)
    
    // Should show QPS, avg latency, connection pool usage
    expect(screen.getByText(/QPS/)).toBeInTheDocument()
    expect(screen.getByText(/Avg Latency/)).toBeInTheDocument()
    expect(screen.getByText(/Pool Usage/)).toBeInTheDocument()
  })

  it('should highlight high resource usage pods', () => {
    render(<NodePodMetrics metrics={mockMetrics} />)
    
    // app-pod-2 has high CPU (70%) and heap (80%) usage
    const highUsagePod = screen.getByTestId('pod-app-pod-2')
    expect(highUsagePod).toHaveClass('border-red-500') // High usage warning
  })

  it('should show pod health status', () => {
    render(<NodePodMetrics metrics={mockMetrics} />)
    
    // Should show health indicators
    expect(screen.getByText('🟢')).toBeInTheDocument() // Healthy pod
    expect(screen.getByText('🟡')).toBeInTheDocument() // Warning pod
  })

  it('should toggle between node view and pod view', () => {
    render(<NodePodMetrics metrics={mockMetrics} />)
    
    // Should have view toggle buttons
    expect(screen.getByRole('button', { name: /Node View/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Pod View/i })).toBeInTheDocument()
    
    // Click pod view
    fireEvent.click(screen.getByRole('button', { name: /Pod View/i }))
    
    // Should show pod-centric view
    expect(screen.getByText('app-pod-1')).toBeInTheDocument()
  })

  it('should show drill-down details when pod is clicked', () => {
    render(<NodePodMetrics metrics={mockMetrics} />)
    
    // Click on a pod to expand details
    fireEvent.click(screen.getByText('app-pod-1'))
    
    // Should show detailed metrics
    expect(screen.getByText(/Connection Pool: 5\/20/)).toBeInTheDocument()
    expect(screen.getByText(/Heap Usage: 60%/)).toBeInTheDocument()
    expect(screen.getByText(/CPU Usage: 40%/)).toBeInTheDocument()
  })

  it('should calculate and display aggregated node metrics', () => {
    render(<NodePodMetrics metrics={mockMetrics} />)
    
    // Should show aggregated metrics for nodes
    // node-1: 2 pods, should aggregate their metrics
    // Average QPS, total connections, etc.
    expect(screen.getByText(/13 connections/)).toBeInTheDocument() // 5+8 from node-1 pods
  })
})