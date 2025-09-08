import React, { useState } from "react";
import { Clock, Eye, Database, User, Play } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Badge } from "@/components/ui/badge";

export default function LongRunningTransactions({ transactions }) {
  const [selectedTransaction, setSelectedTransaction] = useState(null);

  const getDurationColor = (duration) => {
    if (duration > 4000) return "text-red-400 bg-red-500/10";
    if (duration > 2000) return "text-orange-400 bg-orange-500/10";
    return "text-yellow-400 bg-yellow-500/10";
  };

  const formatDuration = (ms) => {
    if (ms > 1000) return `${(ms / 1000).toFixed(1)}s`;
    return `${ms}ms`;
  };

  return (
    <>
      <div className="glass-morphism rounded-2xl p-6 bg-gradient-to-br from-orange-500/10 to-yellow-500/10 border border-orange-500/20 transform hover:scale-105 transition-transform duration-300">
        <div className="flex items-center justify-between mb-6">
          <div className="flex items-center gap-3">
            <div className="p-2 rounded-lg bg-gradient-to-r from-orange-500/20 to-yellow-500/20">
              <Clock className="w-5 h-5 text-orange-400" />
            </div>
            <div>
              <h3 className="text-lg font-bold text-white">Long-Running Transactions</h3>
              <p className="text-white/60 text-sm">Transactions running &gt; 2 seconds</p>
            </div>
          </div>
          <Badge className="bg-orange-500/20 text-orange-400 border-orange-500/30">
            {transactions.length} active
          </Badge>
        </div>

        <div className="space-y-3 max-h-80 overflow-y-auto">
          {transactions.map((transaction, index) => (
            <div
              key={transaction.id}
              className="glass-morphism rounded-lg p-4 border border-white/10 hover:border-orange-500/30 transition-all duration-200 group cursor-pointer"
              onClick={() => setSelectedTransaction(transaction)}
            >
              <div className="flex items-start justify-between">
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 mb-2">
                    <Play className="w-3 h-3 text-green-400 animate-pulse" />
                    <span className="text-white/80 text-sm font-medium">
                      #{transaction.transaction_id}
                    </span>
                    <Badge className={`text-xs ${getDurationColor(transaction.duration)}`}>
                      {formatDuration(transaction.duration)}
                    </Badge>
                  </div>
                  
                  <p className="text-white/60 text-sm truncate mb-2">
                    {transaction.query}
                  </p>
                  
                  <div className="flex items-center gap-4 text-xs text-white/40">
                    <div className="flex items-center gap-1">
                      <Database className="w-3 h-3" />
                      {transaction.database_name}
                    </div>
                    <div className="flex items-center gap-1">
                      <User className="w-3 h-3" />
                      {transaction.user_session}
                    </div>
                  </div>
                </div>
                <Button
                  variant="ghost"
                  size="icon"
                  className="opacity-0 group-hover:opacity-100 transition-opacity h-8 w-8"
                >
                  <Eye className="w-4 h-4 text-white/60" />
                </Button>
              </div>
            </div>
          ))}
          
          {transactions.length === 0 && (
            <div className="text-center py-8">
              <Clock className="w-8 h-8 text-white/20 mx-auto mb-2" />
              <p className="text-white/40 text-sm">No long-running transactions</p>
            </div>
          )}
        </div>
      </div>

      <Dialog open={!!selectedTransaction} onOpenChange={() => setSelectedTransaction(null)}>
        <DialogContent className="glass-morphism border border-white/20 bg-gradient-to-br from-slate-900/90 to-slate-800/90 max-w-2xl">
          <DialogHeader>
            <DialogTitle className="text-white flex items-center gap-2">
              <Clock className="w-5 h-5 text-orange-400" />
              Transaction Details
            </DialogTitle>
          </DialogHeader>
          {selectedTransaction && (
            <div className="space-y-6">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <p className="text-white/60 text-sm">Transaction ID</p>
                  <p className="text-white font-medium">{selectedTransaction.transaction_id}</p>
                </div>
                <div>
                  <p className="text-white/60 text-sm">Status</p>
                  <Badge className="bg-green-500/20 text-green-400 border-green-500/30">
                    {selectedTransaction.status}
                  </Badge>
                </div>
                <div>
                  <p className="text-white/60 text-sm">Duration</p>
                  <p className="text-orange-400 font-bold">{formatDuration(selectedTransaction.duration)}</p>
                </div>
                <div>
                  <p className="text-white/60 text-sm">Database</p>
                  <p className="text-white font-medium">{selectedTransaction.database_name}</p>
                </div>
                <div>
                  <p className="text-white/60 text-sm">User Session</p>
                  <p className="text-white font-medium">{selectedTransaction.user_session}</p>
                </div>
                <div>
                  <p className="text-white/60 text-sm">Started</p>
                  <p className="text-white font-medium">
                    {new Date(selectedTransaction.created_date).toLocaleString()}
                  </p>
                </div>
              </div>
              
              <div>
                <p className="text-white/60 text-sm mb-2">SQL Query</p>
                <div className="glass-morphism rounded-lg p-4 bg-black/20 border border-white/10">
                  <code className="text-white/80 text-sm whitespace-pre-wrap">
                    {selectedTransaction.query}
                  </code>
                </div>
              </div>

              <div className="flex justify-end gap-3">
                <Button variant="outline" className="border-white/20 text-white hover:bg-white/10">
                  View Execution Plan
                </Button>
                <Button className="bg-red-500/20 text-red-400 border border-red-500/30 hover:bg-red-500/30">
                  Kill Transaction
                </Button>
              </div>
            </div>
          )}
        </DialogContent>
      </Dialog>
    </>
  );
}