import React from 'react'
import { render, screen, fireEvent } from '@testing-library/react'
import { LongRunningTransactionAlert } from '../LongRunningTransactionAlert'
import { TransactionEvent } from '@/types/transaction'

describe('LongRunningTransactionAlert', () => {
  const mockLongRunningTransactions: TransactionEvent[] = [
    {
      id: 'evt-1',
      transaction_id: 'tx-slow-1',
      start_time: '2025-08-11T14:25:00Z', // 10 minutes ago
      status: 'active',
      duration_ms: 600000, // 10 minutes
      query_count: 15,
      total_execution_time_ms: 450000,
      pod_name: 'app-pod-1',
      namespace: 'production',
      queries: [
        {
          query_id: 'q-1',
          sql_pattern: 'SELECT * FROM large_table WHERE complex_condition',
          sql_type: 'SELECT',
          execution_time_ms: 30000,
          timestamp: '2025-08-11T14:25:00.100Z',
          sequence_number: 1,
          status: 'success'
        }
      ]
    },
    {
      id: 'evt-2', 
      transaction_id: 'tx-critical-1',
      start_time: '2025-08-11T14:20:00Z', // 15 minutes ago
      status: 'active',
      duration_ms: 900000, // 15 minutes - CRITICAL
      query_count: 3,
      total_execution_time_ms: 120000,
      pod_name: 'app-pod-2',
      queries: []
    }
  ]

  it('should display warning for long running transactions', () => {
    render(<LongRunningTransactionAlert transactions={mockLongRunningTransactions} />)
    
    expect(screen.getByText('⚠️ Long Running Transactions')).toBeInTheDocument()
    expect(screen.getByText(/2 transactions/)).toBeInTheDocument()
  })

  it('should show transaction details with duration warnings', () => {
    render(<LongRunningTransactionAlert transactions={mockLongRunningTransactions} />)
    
    expect(screen.getByText('tx-slow-1')).toBeInTheDocument()
    expect(screen.getByText('tx-critical-1')).toBeInTheDocument()
    
    // Duration formatting
    expect(screen.getByText('10.0m')).toBeInTheDocument() // 10 minutes
    expect(screen.getByText('15.0m')).toBeInTheDocument() // 15 minutes
  })

  it('should categorize transactions by severity', () => {
    render(<LongRunningTransactionAlert transactions={mockLongRunningTransactions} />)
    
    // Should show warning and critical severities
    expect(screen.getByText('WARNING')).toBeInTheDocument()
    expect(screen.getByText('CRITICAL')).toBeInTheDocument()
  })

  it('should show empty state when no long running transactions', () => {
    render(<LongRunningTransactionAlert transactions={[]} />)
    
    expect(screen.getByText('✅ All Transactions Running Normally')).toBeInTheDocument()
    expect(screen.getByText(/No long-running transactions detected/)).toBeInTheDocument()
  })

  it('should provide kill transaction action', () => {
    const onKillTransaction = jest.fn()
    render(
      <LongRunningTransactionAlert 
        transactions={mockLongRunningTransactions}
        onKillTransaction={onKillTransaction}
      />
    )
    
    const killButtons = screen.getAllByRole('button', { name: /Kill/i })
    expect(killButtons).toHaveLength(2)
    
    fireEvent.click(killButtons[0])
    expect(onKillTransaction).toHaveBeenCalledWith('tx-slow-1')
  })

  it('should show pod and namespace information', () => {
    render(<LongRunningTransactionAlert transactions={mockLongRunningTransactions} />)
    
    expect(screen.getByText(/Pod: app-pod-1/)).toBeInTheDocument()
    expect(screen.getByText(/Pod: app-pod-2/)).toBeInTheDocument()
    expect(screen.getByText(/production/)).toBeInTheDocument()
  })

  it('should calculate and display efficiency metrics', () => {
    render(<LongRunningTransactionAlert transactions={mockLongRunningTransactions} />)
    
    // Should show efficiency percentage (execution time vs duration)
    expect(screen.getByText(/75%/)).toBeInTheDocument() // tx-slow-1: 450s/600s
    expect(screen.getByText(/13%/)).toBeInTheDocument() // tx-critical-1: 120s/900s
  })

  it('should filter transactions by duration threshold', () => {
    const shortTransaction: TransactionEvent = {
      id: 'evt-3',
      transaction_id: 'tx-fast-1',
      start_time: '2025-08-11T14:34:30Z',
      status: 'active',
      duration_ms: 30000, // 30 seconds - should not appear
      query_count: 2,
      total_execution_time_ms: 25000,
      queries: []
    }

    render(
      <LongRunningTransactionAlert 
        transactions={[...mockLongRunningTransactions, shortTransaction]}
        thresholdMinutes={5} // Only show transactions > 5 minutes
      />
    )
    
    expect(screen.getByText('tx-slow-1')).toBeInTheDocument()
    expect(screen.getByText('tx-critical-1')).toBeInTheDocument()
    expect(screen.queryByText('tx-fast-1')).not.toBeInTheDocument()
  })
})