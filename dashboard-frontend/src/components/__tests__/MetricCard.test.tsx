/**
 * @jest-environment jsdom
 */

import { render, screen, fireEvent } from '@testing-library/react'
import { MetricCard } from '../MetricCard'

// Mock framer-motion
jest.mock('framer-motion', () => ({
  motion: {
    div: ({ children, className, whileHover, whileTap, animate, ...props }: any) => (
      <div 
        className={className}
        data-testid="motion-div"
        data-animate={JSON.stringify(animate)}
        onMouseEnter={() => whileHover && console.log('hover')}
        onClick={() => whileTap && console.log('tap')}
        {...props}
      >
        {children}
      </div>
    ),
    span: ({ children, className, animate, ...props }: any) => (
      <span 
        className={className}
        data-testid="motion-span"
        data-animate={JSON.stringify(animate)}
        {...props}
      >
        {children}
      </span>
    ),
  },
  AnimatePresence: ({ children }: any) => children,
}))

describe('MetricCard', () => {
  const defaultProps = {
    title: 'QPS',
    value: 1234,
    icon: '🔥',
    change: 12,
    changeLabel: '+12%',
  }

  it('should render basic metric information', () => {
    render(<MetricCard {...defaultProps} />)
    
    expect(screen.getByTestId('metric-card')).toBeInTheDocument()
    expect(screen.getByText('QPS')).toBeInTheDocument()
    expect(screen.getByText('🔥')).toBeInTheDocument()
    expect(screen.getByText('1,234')).toBeInTheDocument()
    expect(screen.getByText('+12%')).toBeInTheDocument()
  })

  it('should format large numbers correctly', () => {
    render(<MetricCard {...defaultProps} value={1234567} />)
    expect(screen.getByText('1,234,567')).toBeInTheDocument()
  })

  it('should format decimal numbers correctly', () => {
    render(<MetricCard {...defaultProps} value={123.456} />)
    expect(screen.getByText('123.46')).toBeInTheDocument()
  })

  it('should show positive change with green color', () => {
    render(<MetricCard {...defaultProps} change={15} changeLabel="↗️ +15%" />)
    
    const changeElement = screen.getByText('↗️ +15%')
    expect(changeElement).toHaveClass('text-green-400')
  })

  it('should show negative change with red color', () => {
    render(<MetricCard {...defaultProps} change={-8} changeLabel="↘️ -8%" />)
    
    const changeElement = screen.getByText('↘️ -8%')
    expect(changeElement).toHaveClass('text-red-400')
  })

  it('should show zero change with gray color', () => {
    render(<MetricCard {...defaultProps} change={0} changeLabel="→ 0%" />)
    
    const changeElement = screen.getByText('→ 0%')
    expect(changeElement).toHaveClass('text-gray-400')
  })

  it('should render with different variants', () => {
    const { rerender } = render(<MetricCard {...defaultProps} variant="primary" />)
    expect(screen.getByTestId('metric-card')).toHaveClass('bg-blue-600/10', 'border-blue-400')

    rerender(<MetricCard {...defaultProps} variant="success" />)
    expect(screen.getByTestId('metric-card')).toHaveClass('bg-green-600/10', 'border-green-400')

    rerender(<MetricCard {...defaultProps} variant="warning" />)
    expect(screen.getByTestId('metric-card')).toHaveClass('bg-yellow-600/10', 'border-yellow-400')

    rerender(<MetricCard {...defaultProps} variant="error" />)
    expect(screen.getByTestId('metric-card')).toHaveClass('bg-red-600/10', 'border-red-400')
  })

  it('should show loading state', () => {
    render(<MetricCard {...defaultProps} isLoading />)
    
    expect(screen.getByTestId('metric-skeleton')).toBeInTheDocument()
    expect(screen.queryByText('1,234')).not.toBeInTheDocument()
  })

  it('should display trend graph when provided', () => {
    const trendData = [
      { timestamp: '10:00', value: 100 },
      { timestamp: '10:01', value: 110 },
      { timestamp: '10:02', value: 105 },
      { timestamp: '10:03', value: 120 },
    ]

    render(<MetricCard {...defaultProps} trendData={trendData} />)
    
    expect(screen.getByTestId('trend-graph')).toBeInTheDocument()
  })

  it('should show alert indicator when threshold is exceeded', () => {
    render(<MetricCard 
      {...defaultProps} 
      value={1500} 
      threshold={1000}
      thresholdType="max"
    />)
    
    expect(screen.getByTestId('alert-indicator')).toBeInTheDocument()
    expect(screen.getByTestId('alert-indicator')).toHaveClass('text-red-400')
  })

  it('should show alert indicator when threshold is not met', () => {
    render(<MetricCard 
      {...defaultProps} 
      value={500} 
      threshold={1000}
      thresholdType="min"
    />)
    
    expect(screen.getByTestId('alert-indicator')).toBeInTheDocument()
    expect(screen.getByTestId('alert-indicator')).toHaveClass('text-red-400')
  })

  it('should not show alert indicator when within threshold', () => {
    render(<MetricCard 
      {...defaultProps} 
      value={800} 
      threshold={1000}
      thresholdType="max"
    />)
    
    expect(screen.queryByTestId('alert-indicator')).not.toBeInTheDocument()
  })

  it('should handle click events', () => {
    const onClickMock = jest.fn()
    render(<MetricCard {...defaultProps} onClick={onClickMock} />)
    
    fireEvent.click(screen.getByTestId('metric-card'))
    expect(onClickMock).toHaveBeenCalledTimes(1)
  })

  it('should show tooltip with additional information', () => {
    render(<MetricCard 
      {...defaultProps} 
      tooltip="Queries executed per second"
    />)
    
    const card = screen.getByTestId('metric-card')
    expect(card).toHaveAttribute('title', 'Queries executed per second')
  })

  it('should display subtitle when provided', () => {
    render(<MetricCard {...defaultProps} subtitle="Last 5 minutes" />)
    
    expect(screen.getByText('Last 5 minutes')).toBeInTheDocument()
  })

  it('should show units for the value', () => {
    render(<MetricCard {...defaultProps} unit="req/s" />)
    
    expect(screen.getByText('req/s')).toBeInTheDocument()
  })

  it('should pulse when value changes significantly', () => {
    const { rerender } = render(<MetricCard {...defaultProps} value={100} />)
    
    rerender(<MetricCard {...defaultProps} value={200} />)
    
    // Check if motion.div has pulse animation
    const motionDiv = screen.getByTestId('motion-div')
    const animateData = JSON.parse(motionDiv.dataset.animate || '{}')
    expect(animateData).toEqual(
      expect.objectContaining({
        scale: [1, 1.02, 1]
      })
    )
  })

  it('should format percentage values correctly', () => {
    render(<MetricCard {...defaultProps} value={0.1234} format="percentage" />)
    
    expect(screen.getByText('12.34%')).toBeInTheDocument()
  })

  it('should format bytes values correctly', () => {
    render(<MetricCard {...defaultProps} value={1024} format="bytes" />)
    
    expect(screen.getByText('1.00 KB')).toBeInTheDocument()
  })

  it('should format time values correctly', () => {
    render(<MetricCard {...defaultProps} value={65000} format="duration" />)
    
    expect(screen.getByText('1m 5s')).toBeInTheDocument()
  })

  it('should show comparison with previous period', () => {
    render(<MetricCard 
      {...defaultProps} 
      value={1234}
      previousValue={1000}
      showComparison
    />)
    
    expect(screen.getByText('+23.4%')).toBeInTheDocument()
  })

  it('should handle missing or invalid data gracefully', () => {
    render(<MetricCard 
      title="Test Metric"
      value={NaN}
      icon="📊"
    />)
    
    expect(screen.getByText('--')).toBeInTheDocument()
  })
})