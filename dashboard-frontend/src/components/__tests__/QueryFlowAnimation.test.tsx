/**
 * @jest-environment jsdom
 */

import { render, screen, waitFor } from '@testing-library/react'
import { QueryFlowAnimation } from '../QueryFlowAnimation'
import { QueryMetrics } from '@/types/metrics'

// Mock framer-motion
jest.mock('framer-motion', () => ({
  motion: {
    div: ({ children, className, animate, initial, ...props }: any) => (
      <div 
        className={className} 
        data-testid="motion-div"
        data-animate={JSON.stringify(animate)}
        data-initial={JSON.stringify(initial)}
        {...props}
      >
        {children}
      </div>
    ),
    path: ({ d, className, animate, initial, ...props }: any) => (
      <path 
        d={d}
        className={className}
        data-testid="motion-path"
        data-animate={JSON.stringify(animate)}
        data-initial={JSON.stringify(initial)}
        {...props}
      />
    ),
  },
  useAnimation: () => ({
    start: jest.fn(),
    stop: jest.fn(),
    set: jest.fn(),
  }),
  AnimatePresence: ({ children }: any) => children,
}))

// Mock the real-time metrics hook
const mockUseRealTimeMetrics = jest.fn()
jest.mock('@/hooks/useRealTimeMetrics', () => ({
  useRealTimeMetrics: () => mockUseRealTimeMetrics(),
}))

describe('QueryFlowAnimation', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    mockUseRealTimeMetrics.mockReturnValue({
      metrics: [],
      isConnected: false,
      connectionStatus: 'disconnected',
      aggregatedMetrics: {
        qps: 0,
        avg_latency: 0,
        error_rate: 0,
        active_connections: 0,
        max_connections: 10,
        heap_usage_ratio: 0,
        cpu_usage_ratio: 0,
      }
    })
  })

  it('should render the basic flow structure', () => {
    render(<QueryFlowAnimation />)
    
    // Check for main flow components
    expect(screen.getByTestId('query-flow-container')).toBeInTheDocument()
    expect(screen.getByTestId('app-node')).toBeInTheDocument()
    expect(screen.getByTestId('pool-node')).toBeInTheDocument()
    expect(screen.getByTestId('db-node')).toBeInTheDocument()
  })

  it('should display connection pool status', () => {
    mockUseRealTimeMetrics.mockReturnValue({
      metrics: [],
      isConnected: true,
      connectionStatus: 'connected',
      aggregatedMetrics: {
        qps: 0,
        avg_latency: 0,
        error_rate: 0,
        active_connections: 5,
        max_connections: 10,
        heap_usage_ratio: 0.6,
        cpu_usage_ratio: 0.3,
      }
    })

    render(<QueryFlowAnimation />)
    
    expect(screen.getByText('💾 5/10')).toBeInTheDocument()
  })

  it('should show active queries when metrics are available', async () => {
    const mockMetrics: QueryMetrics[] = [
      {
        timestamp: '2025-08-09T10:30:45.123Z',
        event_type: 'query_execution',
        data: {
          query_id: 'q_001',
          sql_pattern: 'SELECT * FROM students WHERE id = ?',
          sql_type: 'SELECT',
          execution_time_ms: 45,
          status: 'SUCCESS',
        }
      },
      {
        timestamp: '2025-08-09T10:30:44.123Z',
        event_type: 'query_execution',
        data: {
          query_id: 'q_002',
          sql_pattern: 'INSERT INTO departments VALUES (?)',
          sql_type: 'INSERT',
          execution_time_ms: 12,
          status: 'SUCCESS',
        }
      }
    ]

    mockUseRealTimeMetrics.mockReturnValue({
      metrics: mockMetrics,
      isConnected: true,
      connectionStatus: 'connected',
      aggregatedMetrics: {
        qps: 2,
        avg_latency: 28.5,
        error_rate: 0,
        active_connections: 3,
        max_connections: 10,
        heap_usage_ratio: 0.4,
        cpu_usage_ratio: 0.2,
      }
    })

    render(<QueryFlowAnimation />)
    
    // Should show recent queries
    await waitFor(() => {
      expect(screen.getByText(/SELECT \* FROM students WHERE/)).toBeInTheDocument()
      expect(screen.getByText(/INSERT INTO departments/)).toBeInTheDocument()
    })

    // Should show execution times
    expect(screen.getByText('⏱️ 45ms')).toBeInTheDocument()
    expect(screen.getByText('⏱️ 12ms')).toBeInTheDocument()
  })

  it('should apply different colors based on query execution time', () => {
    const mockMetrics: QueryMetrics[] = [
      {
        timestamp: '2025-08-09T10:30:45.123Z',
        event_type: 'query_execution',
        data: {
          query_id: 'fast_query',
          sql_pattern: 'SELECT COUNT(*) FROM students',
          execution_time_ms: 5, // Fast query (green)
          status: 'SUCCESS',
        }
      },
      {
        timestamp: '2025-08-09T10:30:44.123Z',
        event_type: 'query_execution',
        data: {
          query_id: 'medium_query',
          sql_pattern: 'SELECT * FROM courses JOIN departments',
          execution_time_ms: 25, // Medium query (yellow)
          status: 'SUCCESS',
        }
      },
      {
        timestamp: '2025-08-09T10:30:43.123Z',
        event_type: 'slow_query',
        data: {
          query_id: 'slow_query',
          sql_pattern: 'SELECT * FROM enrollments e JOIN students s',
          execution_time_ms: 150, // Slow query (red)
          status: 'SUCCESS',
        }
      }
    ]

    mockUseRealTimeMetrics.mockReturnValue({
      metrics: mockMetrics,
      isConnected: true,
      connectionStatus: 'connected',
      aggregatedMetrics: {
        qps: 3,
        avg_latency: 60,
        error_rate: 0,
        active_connections: 2,
        max_connections: 10,
        heap_usage_ratio: 0.5,
        cpu_usage_ratio: 0.3,
      }
    })

    render(<QueryFlowAnimation />)
    
    // Check for performance-based styling
    const fastQuery = screen.getByTestId('query-fast_query')
    const mediumQuery = screen.getByTestId('query-medium_query')
    const slowQuery = screen.getByTestId('query-slow_query')
    
    expect(fastQuery).toHaveClass('query-fast')
    expect(mediumQuery).toHaveClass('query-medium')
    expect(slowQuery).toHaveClass('query-slow')
  })

  it('should show error queries with error styling', () => {
    const mockMetrics: QueryMetrics[] = [
      {
        timestamp: '2025-08-09T10:30:45.123Z',
        event_type: 'query_error',
        data: {
          query_id: 'error_query',
          sql_pattern: 'SELECT * FROM non_existent_table',
          execution_time_ms: 0,
          status: 'ERROR',
          error_message: 'Table does not exist',
        }
      }
    ]

    mockUseRealTimeMetrics.mockReturnValue({
      metrics: mockMetrics,
      isConnected: true,
      connectionStatus: 'connected',
      aggregatedMetrics: {
        qps: 1,
        avg_latency: 0,
        error_rate: 100,
        active_connections: 1,
        max_connections: 10,
        heap_usage_ratio: 0.3,
        cpu_usage_ratio: 0.1,
      }
    })

    render(<QueryFlowAnimation />)
    
    const errorQuery = screen.getByTestId('query-error_query')
    expect(errorQuery).toHaveClass('query-error')
    expect(screen.getByText('❌ Error')).toBeInTheDocument()
  })

  it('should animate particles for active queries', () => {
    const mockMetrics: QueryMetrics[] = [
      {
        timestamp: new Date().toISOString(), // Recent timestamp
        event_type: 'query_execution',
        data: {
          query_id: 'active_query',
          sql_pattern: 'SELECT * FROM students',
          execution_time_ms: 25,
          status: 'SUCCESS',
        }
      }
    ]

    mockUseRealTimeMetrics.mockReturnValue({
      metrics: mockMetrics,
      isConnected: true,
      connectionStatus: 'connected',
      aggregatedMetrics: {
        qps: 1,
        avg_latency: 25,
        error_rate: 0,
        active_connections: 1,
        max_connections: 10,
        heap_usage_ratio: 0.2,
        cpu_usage_ratio: 0.1,
      }
    })

    render(<QueryFlowAnimation />)
    
    // Check for animated particles
    const particles = screen.getAllByTestId('query-particle')
    expect(particles.length).toBeGreaterThan(0)
    
    // Check animation properties are set
    const particle = particles[0]
    expect(particle.dataset.animate).toBeDefined()
    expect(particle.dataset.initial).toBeDefined()
  })

  it('should show connection status indicator', () => {
    mockUseRealTimeMetrics.mockReturnValue({
      metrics: [],
      isConnected: false,
      connectionStatus: 'connecting',
      aggregatedMetrics: {
        qps: 0,
        avg_latency: 0,
        error_rate: 0,
        active_connections: 0,
        max_connections: 10,
        heap_usage_ratio: 0,
        cpu_usage_ratio: 0,
      }
    })

    render(<QueryFlowAnimation />)
    
    expect(screen.getByTestId('connection-status')).toBeInTheDocument()
    expect(screen.getByText(/연결 중.../)).toBeInTheDocument()
  })

  it('should limit the number of displayed queries', () => {
    const mockMetrics: QueryMetrics[] = Array.from({ length: 20 }, (_, i) => ({
      timestamp: new Date(Date.now() - i * 1000).toISOString(),
      event_type: 'query_execution' as const,
      data: {
        query_id: `query_${i}`,
        sql_pattern: `SELECT * FROM table_${i}`,
        execution_time_ms: 10 + i,
        status: 'SUCCESS' as const,
      }
    }))

    mockUseRealTimeMetrics.mockReturnValue({
      metrics: mockMetrics,
      isConnected: true,
      connectionStatus: 'connected',
      aggregatedMetrics: {
        qps: 20,
        avg_latency: 20,
        error_rate: 0,
        active_connections: 5,
        max_connections: 10,
        heap_usage_ratio: 0.6,
        cpu_usage_ratio: 0.4,
      }
    })

    render(<QueryFlowAnimation />)
    
    // Should only show max 5 queries (as defined in component)
    const displayedQueries = screen.getAllByTestId(/^query-/)
    expect(displayedQueries).toHaveLength(5)
  })

  it('should handle empty state gracefully', () => {
    render(<QueryFlowAnimation />)
    
    expect(screen.getByTestId('query-flow-container')).toBeInTheDocument()
    expect(screen.getByText('쿼리 대기 중...')).toBeInTheDocument()
  })
})