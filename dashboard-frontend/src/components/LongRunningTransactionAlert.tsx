import React, { useState } from 'react'
import { TransactionEvent, QueryHistoryInfo } from '@/types/transaction'

interface LongRunningTransactionAlertProps {
  transactions: TransactionEvent[]
  onKillTransaction?: (transactionId: string) => void
  thresholdSeconds?: number
  className?: string
}

export function LongRunningTransactionAlert({ 
  transactions, 
  onKillTransaction, 
  thresholdSeconds = 5,
  className = '' 
}: LongRunningTransactionAlertProps) {
  const thresholdMs = thresholdSeconds * 1000

  const longRunningTransactions = transactions.filter(tx => 
    tx.status === 'active' && tx.duration_ms && tx.duration_ms >= thresholdMs
  )

  if (longRunningTransactions.length === 0) {
    return (
      <div className={`bg-green-900/20 border border-green-800 rounded-lg p-4 ${className}`}>
        <div className="flex items-center space-x-3">
          <span className="text-2xl">✅</span>
          <div>
            <h3 className="text-green-400 font-semibold">All Transactions Running Normally</h3>
            <p className="text-green-300/70 text-sm">
              No long-running transactions detected (threshold: {thresholdSeconds}s)
            </p>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className={`space-y-4 ${className}`}>
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-bold text-yellow-400 flex items-center space-x-2">
          <span>⚠️</span>
          <span>Long Running Transactions</span>
        </h2>
        <span className="bg-yellow-900/50 text-yellow-300 px-3 py-1 rounded-full text-sm">
          {longRunningTransactions.length} transactions over {thresholdSeconds}s
        </span>
      </div>

      <div className="space-y-3">
        {longRunningTransactions
          .sort((a, b) => (b.duration_ms || 0) - (a.duration_ms || 0)) // Sort by duration desc
          .map((transaction) => (
            <LongRunningTransactionCard 
              key={transaction.id} 
              transaction={transaction} 
              onKill={onKillTransaction}
              thresholdMs={thresholdMs}
            />
          ))}
      </div>
    </div>
  )
}

function LongRunningTransactionCard({ 
  transaction, 
  onKill,
  thresholdMs 
}: { 
  transaction: TransactionEvent
  onKill?: (id: string) => void
  thresholdMs: number
}) {
  const getSeverity = (durationMs: number) => {
    if (durationMs > thresholdMs * 3) return 'critical' // > 15m (if threshold is 5m)
    if (durationMs > thresholdMs * 2) return 'warning'  // > 10m
    return 'info' // > threshold but < 2x threshold
  }

  const getSeverityColor = (severity: string) => {
    switch (severity) {
      case 'critical': return 'border-red-500 bg-red-900/20'
      case 'warning': return 'border-yellow-500 bg-yellow-900/20'
      default: return 'border-blue-500 bg-blue-900/20'
    }
  }

  const getSeverityBadge = (severity: string) => {
    switch (severity) {
      case 'critical': return 'bg-red-600 text-white'
      case 'warning': return 'bg-yellow-600 text-white'
      default: return 'bg-blue-600 text-white'
    }
  }

  const formatDuration = (ms: number) => {
    if (ms < 60000) return `${Math.round(ms / 1000)}s`
    if (ms < 3600000) return `${(ms / 60000).toFixed(1)}m`
    return `${(ms / 3600000).toFixed(1)}h`
  }

  const calculateEfficiency = (executionMs: number, totalMs: number) => {
    return Math.round((executionMs / totalMs) * 100)
  }

  const severity = getSeverity(transaction.duration_ms || 0)
  const efficiency = calculateEfficiency(
    transaction.total_execution_time_ms, 
    transaction.duration_ms || 1
  )

  return (
    <div className={`border rounded-lg p-4 ${getSeverityColor(severity)}`}>
      <div className="flex items-start justify-between mb-3">
        <div>
          <div className="flex items-center space-x-2">
            <span className={`px-2 py-1 rounded text-xs font-bold uppercase ${getSeverityBadge(severity)}`}>
              {severity}
            </span>
            <span className="font-mono text-blue-300">
              {transaction.transaction_id}
            </span>
            {transaction.pod_name && (
              <span className="bg-gray-700 text-gray-300 px-2 py-1 rounded text-xs">
                Pod: {transaction.pod_name}
              </span>
            )}
            {transaction.namespace && (
              <span className="bg-gray-600 text-gray-300 px-2 py-1 rounded text-xs">
                {transaction.namespace}
              </span>
            )}
          </div>
          <p className="text-gray-300 mt-1 text-sm">
            Running for <strong>{formatDuration(transaction.duration_ms || 0)}</strong> • 
            <span className="ml-1">{transaction.query_count} queries</span>
            {transaction.current_query && (
              <span className="ml-2 text-blue-400">• 🔍 SQL captured</span>
            )}
            {transaction.stored_procedure && (
              <span className="ml-2 text-purple-400">• 📋 Stored procedure</span>
            )}
          </p>
        </div>
        
        {onKill && (
          <button
            onClick={() => onKill(transaction.transaction_id)}
            className="bg-red-600 hover:bg-red-700 text-white px-3 py-1 rounded text-sm transition-colors"
          >
            Kill
          </button>
        )}
      </div>

      <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
        <div>
          <p className="text-gray-400">Started</p>
          <p className="text-gray-200">
            {new Date(transaction.start_time).toLocaleTimeString()}
          </p>
        </div>
        
        <div>
          <p className="text-gray-400">Query Execution</p>
          <p className="text-gray-200">
            {formatDuration(transaction.total_execution_time_ms)}
          </p>
        </div>
        
        <div>
          <p className="text-gray-400">Efficiency</p>
          <p className={`font-semibold ${
            efficiency > 80 ? 'text-green-400' :
            efficiency > 50 ? 'text-yellow-400' : 'text-red-400'
          }`}>
            {efficiency}%
          </p>
        </div>

        <div>
          <p className="text-gray-400">Status</p>
          <div className="flex items-center space-x-1">
            <div className="w-2 h-2 bg-blue-500 rounded-full animate-pulse"></div>
            <span className="text-blue-300">Active</span>
          </div>
        </div>
      </div>

      {/* 현재 실행 중인 쿼리 표시 */}
      {transaction.current_query ? (
        <div className="mt-4 pt-4 border-t border-gray-700">
          <div className="flex items-center space-x-2 mb-2">
            <span className="w-2 h-2 bg-blue-500 rounded-full animate-pulse"></span>
            <p className="text-sm text-blue-400 font-semibold">Currently executing query:</p>
          </div>
          <div className="bg-blue-900/20 border border-blue-700 rounded p-3">
            <div className="font-mono text-xs text-blue-300 whitespace-pre-wrap break-words">
              {transaction.current_query}
            </div>
          </div>
        </div>
      ) : (
        <div className="mt-4 pt-4 border-t border-gray-700">
          <div className="flex items-center space-x-2 mb-2">
            <span className="w-2 h-2 bg-gray-500 rounded-full"></span>
            <p className="text-sm text-gray-500 font-semibold">SQL Query Information:</p>
          </div>
          <div className="bg-yellow-900/20 border border-yellow-700 rounded p-3">
            <div className="flex items-center space-x-2">
              <span className="text-yellow-500">⏳</span>
              <div className="text-xs text-yellow-400">
                <strong>SQL data collection in progress...</strong>
                <div className="text-yellow-300/70 mt-1">
                  The Agent is working to capture SQL query information for this long-running transaction.
                </div>
              </div>
            </div>
          </div>
        </div>
      )}
      
      {/* Stored Procedure 표시 */}
      {transaction.stored_procedure && (
        <div className="mt-4 pt-4 border-t border-gray-700">
          <p className="text-sm text-gray-400 mb-2">Stored Procedure:</p>
          <div className="bg-purple-900/20 border border-purple-700 rounded p-2">
            <div className="font-mono text-sm text-purple-300 font-semibold">
              {transaction.stored_procedure}
            </div>
          </div>
        </div>
      )}
      
      {/* 쿼리 히스토리 표시 */}
      {transaction.query_history && transaction.query_history.length > 0 && (
        <QueryHistorySection queryHistory={transaction.query_history} />
      )}
      
      {/* 최근 쿼리 목록 표시 */}
      {transaction.queries.length > 0 && (
        <div className="mt-4 pt-4 border-t border-gray-700">
          <div className="flex items-center justify-between mb-2">
            <p className="text-sm text-gray-400">Recent queries in this transaction:</p>
            <span className="text-xs text-gray-500 bg-gray-800 px-2 py-1 rounded">
              {transaction.queries.length} total
            </span>
          </div>
          <div className="space-y-2 max-h-40 overflow-y-auto">
            {transaction.queries
              .slice(-3) // Show last 3 queries
              .reverse()  // Most recent first
              .map((query, index) => (
                <div key={query.query_id || index} className="bg-gray-800/50 rounded p-2 border border-gray-600">
                  <div className="flex items-start justify-between mb-1">
                    <span className={`px-2 py-1 rounded text-xs font-bold ${
                      query.sql_type === 'SELECT' ? 'bg-green-700 text-green-100' :
                      query.sql_type === 'INSERT' ? 'bg-blue-700 text-blue-100' :
                      query.sql_type === 'UPDATE' ? 'bg-yellow-700 text-yellow-100' :
                      query.sql_type === 'DELETE' ? 'bg-red-700 text-red-100' :
                      'bg-gray-700 text-gray-100'
                    }`}>
                      {query.sql_type}
                    </span>
                    <div className="text-right">
                      <div className="text-xs text-gray-400">
                        {new Date(query.timestamp).toLocaleTimeString()}
                      </div>
                      <div className="text-xs text-gray-500">
                        {formatDuration(query.execution_time_ms)}
                      </div>
                      {query.rows_affected !== undefined && (
                        <div className="text-xs text-blue-400">
                          {query.rows_affected} rows
                        </div>
                      )}
                    </div>
                  </div>
                  <div className="font-mono text-xs text-gray-300 whitespace-pre-wrap break-words">
                    {query.sql_pattern.length > 150 ? 
                      `${query.sql_pattern.substring(0, 150)}...` : 
                      query.sql_pattern
                    }
                  </div>
                  {query.status === 'error' && query.error_message && (
                    <div className="mt-1 text-xs text-red-400 bg-red-900/20 border border-red-800 rounded p-1">
                      ❌ {query.error_message}
                    </div>
                  )}
                </div>
              ))}
          </div>
        </div>
      )}

      {efficiency < 30 && (
        <div className="mt-3 p-3 bg-red-900/30 border border-red-700 rounded">
          <div className="flex items-center space-x-2">
            <span className="text-red-400">⚠️</span>
            <p className="text-red-300 text-sm">
              <strong>Low efficiency detected!</strong> This transaction is spending most of its time waiting.
              Consider checking for locks or connection issues.
            </p>
          </div>
        </div>
      )}
    </div>
  )
}

function QueryHistorySection({ queryHistory }: { queryHistory: QueryHistoryInfo[] }) {
  const [isExpanded, setIsExpanded] = useState(false)
  
  const formatDuration = (ms: number) => {
    if (ms < 1000) return `${ms}ms`
    if (ms < 60000) return `${Math.round(ms / 1000)}s`
    if (ms < 3600000) return `${(ms / 60000).toFixed(1)}m`
    return `${(ms / 3600000).toFixed(1)}h`
  }

  const formatTimestamp = (timestamp: number) => {
    return new Date(timestamp).toLocaleTimeString()
  }

  return (
    <div className="mt-4 pt-4 border-t border-gray-700">
      <div className="flex items-center justify-between mb-2">
        <p className="text-sm text-gray-400">
          Query History ({queryHistory.length} queries)
        </p>
        <button
          onClick={() => setIsExpanded(!isExpanded)}
          className="text-xs text-blue-400 hover:text-blue-300 transition-colors"
        >
          {isExpanded ? '접기' : '펼치기'}
        </button>
      </div>
      
      {isExpanded && (
        <div className="space-y-2 max-h-60 overflow-y-auto">
          {queryHistory.map((query, index) => (
            <div key={index} className="bg-gray-800/50 rounded p-2 border border-gray-600">
              <div className="flex items-start justify-between mb-1">
                <span className={`px-2 py-1 rounded text-xs font-bold ${
                  query.query_type === 'SELECT' ? 'bg-green-700 text-green-100' :
                  query.query_type === 'INSERT' ? 'bg-blue-700 text-blue-100' :
                  query.query_type === 'UPDATE' ? 'bg-yellow-700 text-yellow-100' :
                  query.query_type === 'DELETE' ? 'bg-red-700 text-red-100' :
                  'bg-gray-700 text-gray-100'
                }`}>
                  {query.query_type}
                </span>
                <div className="text-right">
                  <div className="text-xs text-gray-400">
                    {formatTimestamp(query.start_time)}
                  </div>
                  <div className="text-xs text-gray-500">
                    {formatDuration(query.execution_time)}
                  </div>
                </div>
              </div>
              <div className="font-mono text-xs text-gray-300 whitespace-pre-wrap">
                {query.query.length > 200 ? 
                  `${query.query.substring(0, 200)}...` : 
                  query.query
                }
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}