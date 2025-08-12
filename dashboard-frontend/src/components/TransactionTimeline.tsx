import React, { useState } from 'react'
import { TransactionEvent, getTransactionPerformance } from '@/types/transaction'

interface TransactionTimelineProps {
  transactions: TransactionEvent[]
  className?: string
}

export function TransactionTimeline({ transactions, className = '' }: TransactionTimelineProps) {
  const [expandedTx, setExpandedTx] = useState<string | null>(null)
  const [filter, setFilter] = useState<'all' | 'active' | 'slow'>('all')

  const filteredTransactions = transactions.filter(tx => {
    switch (filter) {
      case 'active': return tx.status === 'active'
      case 'slow': return tx.duration_ms && tx.duration_ms > 1000
      default: return true
    }
  })

  const toggleExpanded = (txId: string) => {
    setExpandedTx(expandedTx === txId ? null : txId)
  }

  if (transactions.length === 0) {
    return (
      <div className={`bg-gray-800 rounded-lg p-6 ${className}`}>
        <h3 className="text-lg font-semibold text-gray-300 mb-4">Transaction Timeline</h3>
        <div className="text-center text-gray-500">
          <p>No transactions to display</p>
          <p className="text-sm mt-1">Transactions will appear here as they are executed</p>
        </div>
      </div>
    )
  }

  return (
    <div className={`bg-gray-800 rounded-lg p-6 ${className}`}>
      <div className="flex items-center justify-between mb-6">
        <h3 className="text-lg font-semibold text-gray-300">Transaction Timeline</h3>
        
        <div className="flex space-x-2">
          <FilterButton 
            active={filter === 'all'} 
            onClick={() => setFilter('all')}
            count={transactions.length}
          >
            All
          </FilterButton>
          <FilterButton 
            active={filter === 'active'} 
            onClick={() => setFilter('active')}
            count={transactions.filter(t => t.status === 'active').length}
          >
            Active
          </FilterButton>
          <FilterButton 
            active={filter === 'slow'} 
            onClick={() => setFilter('slow')}
            count={transactions.filter(t => t.duration_ms && t.duration_ms > 1000).length}
          >
            Slow
          </FilterButton>
        </div>
      </div>

      <div className="space-y-3">
        {filteredTransactions.map((transaction) => (
          <TransactionCard
            key={transaction.id}
            transaction={transaction}
            isExpanded={expandedTx === transaction.transaction_id}
            onToggleExpand={() => toggleExpanded(transaction.transaction_id)}
          />
        ))}
      </div>
    </div>
  )
}

function FilterButton({ 
  active, 
  onClick, 
  count, 
  children 
}: { 
  active: boolean
  onClick: () => void
  count: number
  children: React.ReactNode
}) {
  return (
    <button
      onClick={onClick}
      className={`px-3 py-1 rounded text-sm transition-colors ${
        active 
          ? 'bg-blue-600 text-white' 
          : 'bg-gray-700 text-gray-300 hover:bg-gray-600'
      }`}
    >
      {children} ({count})
    </button>
  )
}

function TransactionCard({ 
  transaction, 
  isExpanded, 
  onToggleExpand 
}: {
  transaction: TransactionEvent
  isExpanded: boolean
  onToggleExpand: () => void
}) {
  const performance = getTransactionPerformance(
    transaction.duration_ms || 0, 
    transaction.query_count
  )

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'committed': return 'bg-green-600'
      case 'active': return 'bg-blue-600 animate-pulse'
      case 'rolled_back': return 'bg-yellow-600'
      default: return 'bg-gray-600'
    }
  }

  const getPerformanceBorder = (performance: string) => {
    switch (performance) {
      case 'fast': return 'border-green-500'
      case 'normal': return 'border-blue-500'
      case 'slow': return 'border-yellow-500'
      case 'critical': return 'border-red-500'
      default: return 'border-gray-600'
    }
  }

  const getPerformanceIndicator = (performance: string) => {
    switch (performance) {
      case 'fast': return '🟢'
      case 'normal': return '🔵'
      case 'slow': return '🟡'
      case 'critical': return '🔴'
      default: return '⚪'
    }
  }

  const formatDuration = (ms?: number) => {
    if (!ms) return 'N/A'
    if (ms < 1000) return `${ms}ms`
    return `${(ms / 1000).toFixed(1)}s`
  }

  const formatTime = (timestamp: string) => {
    return new Date(timestamp).toLocaleTimeString()
  }

  return (
    <div 
      className={`border rounded-lg p-4 cursor-pointer transition-all ${getPerformanceBorder(performance)} bg-gray-900/50 hover:bg-gray-900/70`}
      onClick={onToggleExpand}
      data-testid={`transaction-${transaction.transaction_id}`}
    >
      <div className="flex items-center justify-between">
        <div className="flex items-center space-x-3">
          <span className="text-lg">{getPerformanceIndicator(performance)}</span>
          <div>
            <div className="flex items-center space-x-2">
              <span className="font-mono text-sm text-blue-300">
                {transaction.transaction_id}
              </span>
              <span className={`px-2 py-1 rounded text-xs text-white ${getStatusColor(transaction.status)}`}>
                {transaction.status}
              </span>
              {transaction.pod_name && (
                <span className="px-2 py-1 rounded text-xs bg-gray-700 text-gray-300">
                  Pod: {transaction.pod_name}
                </span>
              )}
            </div>
            <div className="text-sm text-gray-400 mt-1">
              Started: {formatTime(transaction.start_time)}
              {transaction.end_time && ` • Ended: ${formatTime(transaction.end_time)}`}
            </div>
          </div>
        </div>

        <div className="text-right text-sm">
          <div className="text-gray-300">
            {formatDuration(transaction.duration_ms)} • {transaction.query_count} queries
          </div>
          <div className="text-gray-500">
            Total exec: {formatDuration(transaction.total_execution_time_ms)}
          </div>
        </div>
      </div>

      {isExpanded && (
        <div className="mt-4 pt-4 border-t border-gray-700">
          <h5 className="text-sm font-semibold text-gray-300 mb-3">Query Details</h5>
          <div className="space-y-2">
            {transaction.queries.map((query, index) => (
              <div key={`${transaction.transaction_id}-query-${index}`} className="bg-gray-800/50 rounded p-3">
                <div className="flex items-center justify-between mb-2">
                  <div className="flex items-center space-x-2">
                    <span className="bg-gray-600 text-gray-300 px-2 py-1 rounded text-xs">
                      #{query.sequence_number}
                    </span>
                    <span className={`px-2 py-1 rounded text-xs ${
                      query.sql_type === 'SELECT' ? 'bg-blue-600' : 
                      query.sql_type === 'UPDATE' ? 'bg-orange-600' :
                      query.sql_type === 'INSERT' ? 'bg-green-600' :
                      query.sql_type === 'DELETE' ? 'bg-red-600' : 'bg-gray-600'
                    } text-white`}>
                      {query.sql_type}
                    </span>
                  </div>
                  <span className="text-sm text-gray-400">
                    {formatDuration(query.execution_time_ms)}
                  </span>
                </div>
                <div className="font-mono text-xs text-gray-300 bg-gray-900 rounded p-2">
                  {query.sql_pattern}
                </div>
                {query.error_message && (
                  <div className="text-red-400 text-xs mt-2">
                    Error: {query.error_message}
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}