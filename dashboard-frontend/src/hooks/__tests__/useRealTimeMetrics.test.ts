/**
 * @jest-environment jsdom
 */

import { renderHook, act, waitFor } from '@testing-library/react'
import { useRealTimeMetrics } from '../useRealTimeMetrics'
import { QueryMetrics, WebSocketMessage } from '@/types/metrics'

// Mock WebSocket
const mockWebSocket = {
  send: jest.fn(),
  close: jest.fn(),
  addEventListener: jest.fn(),
  removeEventListener: jest.fn(),
  readyState: 1, // WebSocket.OPEN
}

const mockWebSocketConstructor = jest.fn(() => mockWebSocket)
global.WebSocket = mockWebSocketConstructor as any

describe('useRealTimeMetrics', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    mockWebSocketConstructor.mockClear()
    mockWebSocket.addEventListener.mockClear()
    mockWebSocket.close.mockClear()
  })

  it('should initialize with empty metrics and disconnected state', () => {
    const { result } = renderHook(() => useRealTimeMetrics())
    
    expect(result.current.metrics).toEqual([])
    expect(result.current.isConnected).toBe(false)
    expect(result.current.connectionStatus).toBe('connecting')
  })

  it('should establish WebSocket connection on mount', () => {
    renderHook(() => useRealTimeMetrics())
    
    expect(mockWebSocketConstructor).toHaveBeenCalledWith('ws://localhost:8080/ws')
    expect(mockWebSocket.addEventListener).toHaveBeenCalledWith('open', expect.any(Function))
    expect(mockWebSocket.addEventListener).toHaveBeenCalledWith('message', expect.any(Function))
    expect(mockWebSocket.addEventListener).toHaveBeenCalledWith('close', expect.any(Function))
    expect(mockWebSocket.addEventListener).toHaveBeenCalledWith('error', expect.any(Function))
  })

  it('should update connection status when WebSocket opens', async () => {
    const { result } = renderHook(() => useRealTimeMetrics())
    
    // Simulate WebSocket open event
    const openHandler = mockWebSocket.addEventListener.mock.calls.find(
      call => call[0] === 'open'
    )[1]
    
    act(() => {
      mockWebSocket.readyState = 1 // WebSocket.OPEN
      openHandler()
    })

    await waitFor(() => {
      expect(result.current.isConnected).toBe(true)
      expect(result.current.connectionStatus).toBe('connected')
    })
  })

  it('should add new metrics when receiving WebSocket messages', async () => {
    const { result } = renderHook(() => useRealTimeMetrics())
    
    const mockMetrics: QueryMetrics = {
      timestamp: '2025-08-09T10:30:45.123Z',
      pod_name: 'test-pod',
      namespace: 'test-namespace',
      event_type: 'query_execution',
      data: {
        query_id: 'q_001',
        sql_pattern: 'SELECT * FROM students WHERE id = ?',
        sql_type: 'SELECT',
        execution_time_ms: 45,
        status: 'SUCCESS',
      }
    }

    const wsMessage: WebSocketMessage = {
      type: 'query_execution',
      data: mockMetrics,
      timestamp: '2025-08-09T10:30:45.123Z'
    }

    // Simulate WebSocket message
    const messageHandler = mockWebSocket.addEventListener.mock.calls.find(
      call => call[0] === 'message'
    )[1]

    act(() => {
      messageHandler({ data: JSON.stringify(wsMessage) })
    })

    await waitFor(() => {
      expect(result.current.metrics).toHaveLength(1)
      expect(result.current.metrics[0]).toEqual(mockMetrics)
    })
  })

  it('should maintain maximum of 1000 metrics', async () => {
    const { result } = renderHook(() => useRealTimeMetrics())
    
    const messageHandler = mockWebSocket.addEventListener.mock.calls.find(
      call => call[0] === 'message'
    )[1]

    // Add 1001 metrics
    for (let i = 0; i < 1001; i++) {
      const mockMetrics: QueryMetrics = {
        timestamp: new Date().toISOString(),
        event_type: 'query_execution',
        data: {
          query_id: `q_${i}`,
          status: 'SUCCESS',
        }
      }

      const wsMessage: WebSocketMessage = {
        type: 'query_execution',
        data: mockMetrics,
        timestamp: new Date().toISOString()
      }

      act(() => {
        messageHandler({ data: JSON.stringify(wsMessage) })
      })
    }

    await waitFor(() => {
      expect(result.current.metrics).toHaveLength(1000)
      expect(result.current.metrics[0].data?.query_id).toBe('q_1000') // Most recent first
    })
  })

  it('should filter metrics by event type', async () => {
    const { result } = renderHook(() => useRealTimeMetrics({ 
      eventTypes: ['query_execution', 'slow_query'] 
    }))
    
    const messageHandler = mockWebSocket.addEventListener.mock.calls.find(
      call => call[0] === 'message'
    )[1]

    // Send different types of metrics
    const metrics = [
      { event_type: 'query_execution' },
      { event_type: 'slow_query' },
      { event_type: 'system_metrics' }, // Should be filtered out
      { event_type: 'query_error' }, // Should be filtered out
    ]

    for (const metric of metrics) {
      const wsMessage: WebSocketMessage = {
        type: metric.event_type,
        data: { ...metric, timestamp: new Date().toISOString() } as QueryMetrics,
        timestamp: new Date().toISOString()
      }

      act(() => {
        messageHandler({ data: JSON.stringify(wsMessage) })
      })
    }

    await waitFor(() => {
      expect(result.current.metrics).toHaveLength(2)
      expect(result.current.metrics.every(m => 
        m.event_type === 'query_execution' || m.event_type === 'slow_query'
      )).toBe(true)
    })
  })

  it('should calculate aggregated metrics correctly', async () => {
    const { result } = renderHook(() => useRealTimeMetrics())
    
    const messageHandler = mockWebSocket.addEventListener.mock.calls.find(
      call => call[0] === 'message'
    )[1]

    // Add test metrics with known values
    const testMetrics = [
      {
        event_type: 'query_execution',
        data: { execution_time_ms: 10, status: 'SUCCESS' },
        metrics: { connection_pool_active: 5, connection_pool_max: 10 }
      },
      {
        event_type: 'query_execution', 
        data: { execution_time_ms: 20, status: 'SUCCESS' },
        metrics: { connection_pool_active: 6, connection_pool_max: 10 }
      },
      {
        event_type: 'query_execution',
        data: { execution_time_ms: 30, status: 'ERROR' },
        metrics: { connection_pool_active: 7, connection_pool_max: 10 }
      }
    ]

    for (const metric of testMetrics) {
      const wsMessage: WebSocketMessage = {
        type: 'query_execution',
        data: { ...metric, timestamp: new Date().toISOString() } as QueryMetrics,
        timestamp: new Date().toISOString()
      }

      act(() => {
        messageHandler({ data: JSON.stringify(wsMessage) })
      })
    }

    await waitFor(() => {
      const aggregated = result.current.aggregatedMetrics
      expect(aggregated.avg_latency).toBe(20) // (10 + 20 + 30) / 3
      expect(aggregated.error_rate).toBeCloseTo(33.33) // 1 error out of 3 queries
      expect(aggregated.active_connections).toBe(7) // Most recent value
      expect(aggregated.max_connections).toBe(10)
    })
  })

  it('should handle connection errors gracefully', async () => {
    const { result } = renderHook(() => useRealTimeMetrics())
    
    const errorHandler = mockWebSocket.addEventListener.mock.calls.find(
      call => call[0] === 'error'
    )[1]

    act(() => {
      errorHandler(new Error('Connection failed'))
    })

    await waitFor(() => {
      expect(result.current.connectionStatus).toBe('error')
      expect(result.current.isConnected).toBe(false)
    })
  })

  it('should handle connection close gracefully', async () => {
    const { result } = renderHook(() => useRealTimeMetrics())
    
    const closeHandler = mockWebSocket.addEventListener.mock.calls.find(
      call => call[0] === 'close'
    )[1]

    act(() => {
      mockWebSocket.readyState = 3 // WebSocket.CLOSED
      closeHandler()
    })

    await waitFor(() => {
      expect(result.current.connectionStatus).toBe('disconnected')
      expect(result.current.isConnected).toBe(false)
    })
  })

  it('should reconnect automatically after connection loss', async () => {
    jest.useFakeTimers()
    
    const { result } = renderHook(() => useRealTimeMetrics({ autoReconnect: true }))
    
    const closeHandler = mockWebSocket.addEventListener.mock.calls.find(
      call => call[0] === 'close'
    )[1]

    act(() => {
      mockWebSocket.readyState = 3 // WebSocket.CLOSED
      closeHandler()
    })

    // Fast-forward time to trigger reconnection
    act(() => {
      jest.advanceTimersByTime(5000) // 5 seconds
    })

    expect(mockWebSocketConstructor).toHaveBeenCalledTimes(2) // Initial + reconnect
    
    jest.useRealTimers()
  })

  it('should clean up WebSocket connection on unmount', () => {
    const { unmount } = renderHook(() => useRealTimeMetrics())
    
    unmount()
    
    expect(mockWebSocket.close).toHaveBeenCalled()
  })

  it('should ignore invalid JSON messages', async () => {
    const { result } = renderHook(() => useRealTimeMetrics())
    
    const messageHandler = mockWebSocket.addEventListener.mock.calls.find(
      call => call[0] === 'message'
    )[1]

    act(() => {
      messageHandler({ data: 'invalid json' })
    })

    await waitFor(() => {
      expect(result.current.metrics).toHaveLength(0)
    })
  })
})