import React from 'react'
import { render, screen } from '@testing-library/react'
import { DeadlockAlert } from '../DeadlockAlert'

describe('DeadlockAlert', () => {
  const mockDeadlockEvent = {
    id: 'dl-1',
    participants: ['tx-1', 'tx-2'],
    detectionTime: '2025-08-11T14:35:00Z',
    recommendedVictim: 'tx-2',
    lockChain: ['tx-1 -> users', 'tx-2 -> orders', 'tx-1 <- users'],
    severity: 'critical' as const,
    status: 'active' as const
  }

  it('should render deadlock alert with critical severity', () => {
    render(<DeadlockAlert deadlocks={[mockDeadlockEvent]} />)
    
    expect(screen.getByText('🚨 Deadlock Alert')).toBeInTheDocument()
    expect(screen.getByText('Critical')).toBeInTheDocument()
    expect(screen.getByText(/2 transactions involved/)).toBeInTheDocument()
  })

  it('should display recommended victim transaction', () => {
    render(<DeadlockAlert deadlocks={[mockDeadlockEvent]} />)
    
    expect(screen.getByText(/Recommended victim: tx-2/)).toBeInTheDocument()
  })

  it('should show lock chain visualization', () => {
    render(<DeadlockAlert deadlocks={[mockDeadlockEvent]} />)
    
    expect(screen.getByText(/Lock Chain:/)).toBeInTheDocument()
    expect(screen.getByText(/tx-1.*users/)).toBeInTheDocument()
    expect(screen.getByText(/tx-2.*orders/)).toBeInTheDocument()
  })

  it('should render empty state when no deadlocks', () => {
    render(<DeadlockAlert deadlocks={[]} />)
    
    expect(screen.getByText('✅ No Active Deadlocks')).toBeInTheDocument()
    expect(screen.getByText(/System is running smoothly/)).toBeInTheDocument()
  })

  it('should show multiple deadlock events', () => {
    const multipleDeadlocks = [
      mockDeadlockEvent,
      {
        ...mockDeadlockEvent,
        id: 'dl-2',
        participants: ['tx-3', 'tx-4', 'tx-5'],
        recommendedVictim: 'tx-4'
      }
    ]

    render(<DeadlockAlert deadlocks={multipleDeadlocks} />)
    
    expect(screen.getByText('2 Active Deadlocks')).toBeInTheDocument()
  })

  it('should format detection time correctly', () => {
    render(<DeadlockAlert deadlocks={[mockDeadlockEvent]} />)
    
    // Should show relative time
    expect(screen.getByText(/Detected:/)).toBeInTheDocument()
  })

  it('should show resolve action button', () => {
    const onResolve = jest.fn()
    render(<DeadlockAlert deadlocks={[mockDeadlockEvent]} onResolve={onResolve} />)
    
    const resolveButton = screen.getByRole('button', { name: /Resolve/i })
    expect(resolveButton).toBeInTheDocument()
  })
})