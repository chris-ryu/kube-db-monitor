import React from 'react'
import { DeadlockEvent } from '@/types/deadlock'

interface DeadlockAlertProps {
  deadlocks: DeadlockEvent[]
  onResolve?: (deadlockId: string) => void
  className?: string
}

export function DeadlockAlert({ deadlocks, onResolve, className = '' }: DeadlockAlertProps) {
  const activeDeadlocks = deadlocks.filter(d => d.status === 'active')

  if (activeDeadlocks.length === 0) {
    return (
      <div className={`bg-green-900/20 border border-green-800 rounded-lg p-4 ${className}`}>
        <div className="flex items-center space-x-3">
          <span className="text-2xl">✅</span>
          <div>
            <h3 className="text-green-400 font-semibold">No Active Deadlocks</h3>
            <p className="text-green-300/70 text-sm">System is running smoothly</p>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className={`space-y-4 ${className}`}>
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-bold text-red-400 flex items-center space-x-2">
          <span>🚨</span>
          <span>Deadlock Alert</span>
        </h2>
        {activeDeadlocks.length > 1 && (
          <span className="bg-red-900/50 text-red-300 px-3 py-1 rounded-full text-sm">
            {activeDeadlocks.length} Active Deadlocks
          </span>
        )}
      </div>

      <div className="space-y-3">
        {activeDeadlocks.map((deadlock) => (
          <DeadlockCard 
            key={deadlock.id} 
            deadlock={deadlock} 
            onResolve={onResolve}
          />
        ))}
      </div>
    </div>
  )
}

function DeadlockCard({ deadlock, onResolve }: { 
  deadlock: DeadlockEvent
  onResolve?: (id: string) => void 
}) {
  const getSeverityColor = (severity: string) => {
    switch (severity) {
      case 'critical': return 'border-red-500 bg-red-900/20'
      case 'warning': return 'border-yellow-500 bg-yellow-900/20'
      default: return 'border-blue-500 bg-blue-900/20'
    }
  }

  const formatTime = (timestamp: string) => {
    const date = new Date(timestamp)
    const now = new Date()
    const diffMs = now.getTime() - date.getTime()
    const diffSec = Math.floor(diffMs / 1000)
    
    if (diffSec < 60) return `${diffSec}s ago`
    if (diffSec < 3600) return `${Math.floor(diffSec / 60)}m ago`
    return `${Math.floor(diffSec / 3600)}h ago`
  }

  return (
    <div className={`border rounded-lg p-4 ${getSeverityColor(deadlock.severity)}`}>
      <div className="flex items-start justify-between mb-3">
        <div>
          <div className="flex items-center space-x-2">
            <span className="bg-red-600 text-white px-2 py-1 rounded text-xs font-bold uppercase">
              {deadlock.severity}
            </span>
            {deadlock.pod_name && (
              <span className="bg-gray-700 text-gray-300 px-2 py-1 rounded text-xs">
                Pod: {deadlock.pod_name}
              </span>
            )}
          </div>
          <p className="text-gray-300 mt-1">
            <strong>{deadlock.participants.length} transactions involved:</strong>{' '}
            {deadlock.participants.map((participant, index) => (
              <span key={index}>
                {typeof participant === 'string' 
                  ? participant 
                  : (participant as any).id || (participant as any).connection || JSON.stringify(participant)
                }
                {index < deadlock.participants.length - 1 && ', '}
              </span>
            ))}
          </p>
        </div>
        
        {onResolve && (
          <button
            onClick={() => onResolve(deadlock.id)}
            className="bg-red-600 hover:bg-red-700 text-white px-3 py-1 rounded text-sm transition-colors"
          >
            Resolve
          </button>
        )}
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-3">
        <div>
          <p className="text-sm text-gray-400">
            <strong>Detected:</strong> {formatTime(deadlock.detectionTime)}
          </p>
          <p className="text-sm text-gray-400">
            <strong>Recommended victim:</strong>{' '}
            <span className="text-red-300 font-mono">{deadlock.recommendedVictim}</span>
          </p>
        </div>
        
        {deadlock.cycleLength && (
          <div>
            <p className="text-sm text-gray-400">
              <strong>Cycle Length:</strong> {deadlock.cycleLength} transactions
            </p>
          </div>
        )}
      </div>

      {deadlock.lockChain.length > 0 && (
        <div>
          <p className="text-sm font-medium text-gray-300 mb-2">Lock Chain:</p>
          <div className="bg-gray-800/50 rounded p-3">
            <div className="font-mono text-xs space-y-1">
              {deadlock.lockChain.map((chain, index) => (
                <div key={index} className="flex items-center space-x-2">
                  <span className="text-gray-500">{index + 1}.</span>
                  <span className="text-yellow-300">
                    {typeof chain === 'string' 
                      ? chain 
                      : `${(chain as any).from || 'unknown'} → ${(chain as any).to || 'unknown'} (${(chain as any).resource || 'unknown resource'}, ${(chain as any).lockType || 'unknown lock'})`
                    }
                  </span>
                  {index < deadlock.lockChain.length - 1 && (
                    <span className="text-red-400">↓</span>
                  )}
                </div>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}