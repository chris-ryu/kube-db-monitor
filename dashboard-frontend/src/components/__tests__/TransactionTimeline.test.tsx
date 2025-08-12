import React from 'react'
import { render, screen } from '@testing-library/react'
import { TransactionTimeline } from '../TransactionTimeline'
import { TransactionEvent } from '@/types/transaction'

describe('TransactionTimeline', () => {
  const mockTransactions: TransactionEvent[] = [
    {
      id: 'evt-1',
      transaction_id: 'tx-1',
      start_time: '2025-08-11T14:30:00Z',
      end_time: '2025-08-11T14:30:02Z',
      status: 'committed',
      duration_ms: 2000,
      query_count: 3,
      total_execution_time_ms: 1800,
      pod_name: 'app-pod-1',
      queries: [
        {
          query_id: 'q-1',
          sql_pattern: 'SELECT * FROM users',
          sql_type: 'SELECT',
          execution_time_ms: 500,
          timestamp: '2025-08-11T14:30:00.100Z',
          sequence_number: 1,
          status: 'success'
        },
        {
          query_id: 'q-2',
          sql_pattern: 'UPDATE users SET name = ?',
          sql_type: 'UPDATE',
          execution_time_ms: 800,
          timestamp: '2025-08-11T14:30:01.000Z',
          sequence_number: 2,
          status: 'success'
        }
      ]
    },
    {
      id: 'evt-2',
      transaction_id: 'tx-2',
      start_time: '2025-08-11T14:30:01Z',
      status: 'active',
      duration_ms: 8000, // Long running
      query_count: 1,
      total_execution_time_ms: 500,
      pod_name: 'app-pod-2',
      queries: [
        {
          query_id: 'q-3',
          sql_pattern: 'SELECT * FROM orders FOR UPDATE',
          sql_type: 'SELECT',
          execution_time_ms: 500,
          timestamp: '2025-08-11T14:30:01.200Z',
          sequence_number: 1,
          status: 'success'
        }
      ]
    }
  ]

  it('should render transaction timeline with transactions', () => {
    render(<TransactionTimeline transactions={mockTransactions} />)
    
    expect(screen.getByText('Transaction Timeline')).toBeInTheDocument()
    expect(screen.getByText('tx-1')).toBeInTheDocument()
    expect(screen.getByText('tx-2')).toBeInTheDocument()
  })

  it('should show transaction status with correct colors', () => {
    render(<TransactionTimeline transactions={mockTransactions} />)
    
    // Committed transaction should have green status
    expect(screen.getByText('committed')).toBeInTheDocument()
    // Active transaction should have blue status  
    expect(screen.getByText('active')).toBeInTheDocument()
  })

  it('should display transaction duration and query count', () => {
    render(<TransactionTimeline transactions={mockTransactions} />)
    
    expect(screen.getByText('2.0s')).toBeInTheDocument() // Duration
    expect(screen.getByText('3 queries')).toBeInTheDocument()
    expect(screen.getByText('1 queries')).toBeInTheDocument()
  })

  it('should highlight long running transactions', () => {
    render(<TransactionTimeline transactions={mockTransactions} />)
    
    // tx-2 should be marked as slow/critical (8s duration)
    const longRunningTx = screen.getByTestId('transaction-tx-2')
    expect(longRunningTx).toHaveClass('border-red-500') // Critical performance
  })

  it('should show pod information when available', () => {
    render(<TransactionTimeline transactions={mockTransactions} />)
    
    expect(screen.getByText(/Pod: app-pod-1/)).toBeInTheDocument()
    expect(screen.getByText(/Pod: app-pod-2/)).toBeInTheDocument()
  })

  it('should render empty state when no transactions', () => {
    render(<TransactionTimeline transactions={[]} />)
    
    expect(screen.getByText(/No transactions/)).toBeInTheDocument()
  })

  it('should expand transaction details when clicked', () => {
    render(<TransactionTimeline transactions={mockTransactions} />)
    
    // Should show query details when expanded
    expect(screen.getByText('SELECT * FROM users')).toBeInTheDocument()
    expect(screen.getByText('UPDATE users SET name = ?')).toBeInTheDocument()
  })

  it('should show performance indicators', () => {
    render(<TransactionTimeline transactions={mockTransactions} />)
    
    // Fast transaction (tx-1: 2s, normal range)
    // Critical transaction (tx-2: 8s, critical range)
    const criticalIndicator = screen.getByText('🔴') // Critical performance indicator
    expect(criticalIndicator).toBeInTheDocument()
  })
})