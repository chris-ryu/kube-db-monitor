import React, { useState } from 'react'
import { DeadlockEvent } from '@/types/deadlock'
import { AlertTriangle, Eye, Clock, Database } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'

interface DeadlockAlertProps {
  deadlocks: DeadlockEvent[]
  onResolve?: (deadlockId: string) => void
  className?: string
}

export function DeadlockAlert({ deadlocks, onResolve, className = '' }: DeadlockAlertProps) {
  const [selectedDeadlock, setSelectedDeadlock] = useState<DeadlockEvent | null>(null)
  const activeDeadlocks = deadlocks.filter(d => d.status === 'active')

  return (
    <>
      <div className="glass-morphism rounded-2xl p-6 bg-gradient-to-br from-red-500/10 to-pink-500/10 border border-red-500/20 transform hover:scale-105 transition-transform duration-300">
        <div className="flex items-center justify-between mb-4">
          <div className="flex items-center gap-3">
            <div className="p-2 rounded-lg bg-gradient-to-r from-red-500/20 to-pink-500/20">
              <AlertTriangle className="w-5 h-5 text-red-400" />
            </div>
            <div>
              <h3 className="text-lg font-bold text-white">Deadlocks</h3>
              <p className="text-white/60 text-sm">Failed transactions</p>
            </div>
          </div>
          <div className="text-2xl font-bold text-red-400">
            {activeDeadlocks.length}
          </div>
        </div>

        <div className="space-y-3 max-h-64 overflow-y-auto">
          {activeDeadlocks.map((deadlock) => (
            <div
              key={deadlock.id}
              className="glass-morphism rounded-lg p-3 border border-white/10 hover:border-red-500/30 transition-all duration-200 group"
            >
              <div className="flex items-start justify-between">
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 mb-1">
                    <Database className="w-3 h-3 text-white/60 flex-shrink-0" />
                    <p className="text-white/80 text-xs font-medium truncate">
                      {deadlock.pod_name || 'Database'}
                    </p>
                    <span className="text-red-400 text-xs">#{deadlock.id}</span>
                  </div>
                  <p className="text-white/60 text-xs truncate">
                    Deadlock detected
                  </p>
                  <div className="flex items-center gap-2 mt-1">
                    <Clock className="w-3 h-3 text-white/40" />
                    <span className="text-white/40 text-xs">
                      {new Date(deadlock.detectionTime).toLocaleTimeString()}
                    </span>
                  </div>
                </div>
                <Button
                  variant="ghost"
                  size="icon"
                  className="opacity-0 group-hover:opacity-100 transition-opacity h-6 w-6"
                  onClick={() => setSelectedDeadlock(deadlock)}
                >
                  <Eye className="w-3 h-3 text-white/60" />
                </Button>
              </div>
            </div>
          ))}
          
          {activeDeadlocks.length === 0 && (
            <div className="text-center py-8">
              <AlertTriangle className="w-8 h-8 text-white/20 mx-auto mb-2" />
              <p className="text-white/40 text-sm">No deadlocks detected</p>
            </div>
          )}
        </div>
      </div>

      <Dialog open={!!selectedDeadlock} onOpenChange={() => setSelectedDeadlock(null)}>
        <DialogContent className="glass-morphism border border-white/20 bg-gradient-to-br from-slate-900/90 to-slate-800/90">
          <DialogHeader>
            <DialogTitle className="text-white flex items-center gap-2">
              <AlertTriangle className="w-5 h-5 text-red-400" />
              Deadlock Details
            </DialogTitle>
          </DialogHeader>
          {selectedDeadlock && (
            <div className="space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <p className="text-white/60 text-sm">Transaction ID</p>
                  <p className="text-white font-medium">{selectedDeadlock.id}</p>
                </div>
                <div>
                  <p className="text-white/60 text-sm">Database</p>
                  <p className="text-white font-medium">{selectedDeadlock.pod_name || 'N/A'}</p>
                </div>
                <div>
                  <p className="text-white/60 text-sm">Duration</p>
                  <p className="text-white font-medium">{selectedDeadlock.duration_ms}ms</p>
                </div>
                <div>
                  <p className="text-white/60 text-sm">Session</p>
                  <p className="text-white font-medium">{selectedDeadlock.pod_name || 'N/A'}</p>
                </div>
              </div>
              <div>
                <p className="text-white/60 text-sm mb-2">Connections</p>
                <div className="glass-morphism rounded-lg p-3 bg-black/20 border border-white/10">
                  <code className="text-white/80 text-sm">{selectedDeadlock.connections}</code>
                </div>
              </div>
              {selectedDeadlock.participants.length > 0 && (
                <div>
                  <p className="text-white/60 text-sm mb-2">Participants</p>
                  <div className="glass-morphism rounded-lg p-3 bg-black/20 border border-white/10">
                    <div className="space-y-1">
                      {selectedDeadlock.participants.map((participant, index) => (
                        <p key={index} className="text-white/80 text-sm font-mono">
                          {typeof participant === 'string' 
                            ? participant 
                            : JSON.stringify(participant)
                          }
                        </p>
                      ))}
                    </div>
                  </div>
                </div>
              )}
            </div>
          )}
        </DialogContent>
      </Dialog>
    </>
  )
}

