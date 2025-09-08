import React, { useState } from 'react'
import { TransactionEvent, getTransactionPerformance } from '@/types/transaction'
import { TrendingUp, Hourglass, Clock, CheckCircle2, XCircle, Play, Package, Search, AlertCircle } from 'lucide-react'

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
      <div className={`relative ${className}`}>
        <h3 className="text-2xl font-bold text-white mb-6 flex items-center gap-3">
          <TrendingUp className="w-8 h-8 text-blue-400" />
          <span className="gradient-text">Transaction Timeline</span>
        </h3>
        <div className="glass-dark p-12 rounded-xl text-center">
          <Hourglass className="w-24 h-24 mb-4 opacity-50 mx-auto" />
          <p className="text-gray-200 text-lg font-medium">No transactions to display</p>
          <p className="text-gray-400 text-sm mt-2">Transactions will appear here as they are executed</p>
        </div>
      </div>
    )
  }

  return (
    <div className={`relative ${className}`}>
      <div className="flex flex-col md:flex-row md:items-center justify-between mb-8 gap-4">
        <h3 className="text-2xl font-bold text-white flex items-center gap-3">
          <TrendingUp className="w-8 h-8 text-blue-400" />
          <span className="gradient-text">Transaction Timeline</span>
        </h3>
        
        <div className="flex space-x-3">
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

      <div className="space-y-4">
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
      className={`glass-card px-4 py-2 rounded-lg text-sm font-medium transition-all duration-200 hover:scale-105 ${
        active 
          ? 'border-blue-400/50 bg-blue-500/20 text-blue-200 shadow-lg' 
          : 'text-gray-300 hover:text-white hover:bg-white/10'
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
      case 'committed': return 'border-green-400/50 bg-green-500/20 text-green-200'
      case 'active': return 'border-blue-400/50 bg-blue-500/20 text-blue-200 animate-pulse'
      case 'rolled_back': return 'border-yellow-400/50 bg-yellow-500/20 text-yellow-200'
      default: return 'border-gray-400/50 bg-gray-500/20 text-gray-200'
    }
  }

  const getPerformanceBorder = (performance: string) => {
    switch (performance) {
      case 'fast': return 'border-green-400/40'
      case 'normal': return 'border-blue-400/40'
      case 'slow': return 'border-yellow-400/40'
      case 'critical': return 'border-red-400/40'
      default: return 'border-gray-400/40'
    }
  }

  const getPerformanceIndicator = (performance: string) => {
    switch (performance) {
      case 'fast': return <CheckCircle2 className="w-6 h-6 text-green-400" />
      case 'normal': return <Clock className="w-6 h-6 text-blue-400" />
      case 'slow': return <AlertCircle className="w-6 h-6 text-yellow-400" />
      case 'critical': return <XCircle className="w-6 h-6 text-red-400" />
      default: return <Clock className="w-6 h-6 text-gray-400" />
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
      className={`glass-card border ${getPerformanceBorder(performance)} rounded-xl p-6 cursor-pointer transition-all duration-300 hover:scale-[1.02] glass-hover relative overflow-hidden`}
      onClick={onToggleExpand}
      data-testid={`transaction-${transaction.transaction_id}`}
    >
      <div className="absolute inset-0 bg-gradient-to-br from-white/5 via-transparent to-white/10 pointer-events-none"></div>
      
      <div className="relative z-10">
        <div className="flex items-center justify-between mb-4">
          <div className="flex items-center space-x-4">
            {getPerformanceIndicator(performance)}
            <div>
              <div className="flex items-center space-x-3 mb-2">
                <span className="font-mono text-sm text-blue-300 bg-blue-500/10 px-2 py-1 rounded">
                  {transaction.transaction_id}
                </span>
                <span className={`glass-dark px-3 py-1.5 rounded-lg text-sm font-semibold ${getStatusColor(transaction.status)}`}>
                  {transaction.status}
                </span>
                {transaction.pod_name && (
                  <span className="glass-dark border border-gray-400/30 text-gray-200 px-2 py-1 rounded text-sm flex items-center gap-1">
                    <Package className="w-3 h-3" />
                    {transaction.pod_name}
                  </span>
                )}
              </div>
              <div className="text-sm text-gray-300">
                Started: {formatTime(transaction.start_time)}
                {transaction.end_time && ` • Ended: ${formatTime(transaction.end_time)}`}
              </div>
            </div>
          </div>

          <div className="text-right">
            <div className="flex items-center gap-4 text-sm">
              <div className="glass-dark p-2 rounded">
                <span className="text-gray-300">Duration:</span>
                <span className="ml-1 font-bold text-white">{formatDuration(transaction.duration_ms)}</span>
              </div>
              <div className="glass-dark p-2 rounded">
                <span className="text-gray-300">Queries:</span>
                <span className="ml-1 font-bold text-white">{transaction.query_count}</span>
              </div>
            </div>
            <div className="text-xs text-gray-400 mt-1">
              Total exec: {formatDuration(transaction.total_execution_time_ms)}
            </div>
          </div>
        </div>

        {isExpanded && (
          <div className="mt-6 pt-6 border-t border-white/10">
            <h5 className="text-lg font-bold text-white mb-4 flex items-center gap-2">
              <Search className="w-5 h-5" />
              Query Details
            </h5>
            <div className="space-y-3">
              {transaction.queries.map((query, index) => (
                <div key={`${transaction.transaction_id}-query-${index}`} className="glass-dark p-4 rounded-lg">
                  <div className="flex items-center justify-between mb-3">
                    <div className="flex items-center space-x-3">
                      <span className="glass-card w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold text-blue-300">
                        #{query.sequence_number}
                      </span>
                      <span className={`px-3 py-1 rounded-lg text-xs font-semibold ${
                        query.sql_type === 'SELECT' ? 'bg-blue-500/30 text-blue-200 border border-blue-400/30' : 
                        query.sql_type === 'UPDATE' ? 'bg-orange-500/30 text-orange-200 border border-orange-400/30' :
                        query.sql_type === 'INSERT' ? 'bg-green-500/30 text-green-200 border border-green-400/30' :
                        query.sql_type === 'DELETE' ? 'bg-red-500/30 text-red-200 border border-red-400/30' : 
                        query.sql_type === 'TPS_EVENT' ? 'bg-purple-500/30 text-purple-200 border border-purple-400/30' : 
                        'bg-gray-500/30 text-gray-200 border border-gray-400/30'
                      }`}>
                        {query.sql_type}
                      </span>
                    </div>
                    <span className="text-sm text-gray-300 font-medium">
                      {formatDuration(query.execution_time_ms)}
                    </span>
                  </div>
                  <div className="font-mono text-sm text-gray-200 bg-black/30 rounded p-3 border border-white/10">
                    {query.sql_pattern}
                  </div>
                  {query.error_message && (
                    <div className="text-red-300 text-sm mt-2 p-2 bg-red-500/10 border border-red-400/30 rounded">
                      <XCircle className="w-4 h-4 inline mr-1" />
                      Error: {query.error_message}
                    </div>
                  )}
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  )
}