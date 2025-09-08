import React, { useState } from "react";
import { Clock, CheckCircle2, XCircle, Play, Database, Filter } from "lucide-react";
import { Badge } from "@/components/ui/badge";

export default function TransactionTimeline({ transactions }) {
  const [filter, setFilter] = useState("all");
  
  const filteredTransactions = transactions.filter(t => {
    if (filter === "all") return true;
    return t.status === filter;
  });

  const getStatusIcon = (status) => {
    switch (status) {
      case "completed":
        return <CheckCircle2 className="w-4 h-4 text-green-400" />;
      case "failed":
        return <XCircle className="w-4 h-4 text-red-400" />;
      case "active":
        return <Play className="w-4 h-4 text-blue-400 animate-pulse" />;
      default:
        return <Clock className="w-4 h-4 text-yellow-400" />;
    }
  };

  const getStatusColor = (status) => {
    switch (status) {
      case "completed":
        return "bg-green-500/20 text-green-400 border-green-500/30";
      case "failed": 
        return "bg-red-500/20 text-red-400 border-red-500/30";
      case "active":
        return "bg-blue-500/20 text-blue-400 border-blue-500/30";
      default:
        return "bg-yellow-500/20 text-yellow-400 border-yellow-500/30";
    }
  };

  return (
    <div className="glass-morphism rounded-2xl p-6 bg-gradient-to-br from-blue-500/10 to-cyan-500/10 border border-blue-500/20 transform hover:scale-105 transition-transform duration-300">
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center gap-3">
          <div className="p-2 rounded-lg bg-gradient-to-r from-blue-500/20 to-cyan-500/20">
            <Clock className="w-5 h-5 text-blue-400" />
          </div>
          <div>
            <h3 className="text-lg font-bold text-white">Transaction Timeline</h3>
            <p className="text-white/60 text-sm">Recent transaction activity</p>
          </div>
        </div>
        
        <div className="flex items-center gap-2">
          <Filter className="w-4 h-4 text-white/60" />
          <select
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
            className="glass-morphism rounded-lg px-3 py-1 text-sm text-white bg-transparent border border-white/20 focus:outline-none focus:border-blue-500/50"
          >
            <option value="all" className="bg-slate-800">All</option>
            <option value="active" className="bg-slate-800">Active</option>
            <option value="completed" className="bg-slate-800">Completed</option>
            <option value="failed" className="bg-slate-800">Failed</option>
          </select>
        </div>
      </div>

      <div className="space-y-3 max-h-80 overflow-y-auto">
        {filteredTransactions.map((transaction, index) => (
          <div
            key={transaction.id}
            className="relative"
          >
            <div className="flex items-start gap-4 glass-morphism rounded-lg p-4 border border-white/10 hover:border-blue-500/30 transition-all duration-200">
              {/* Timeline dot */}
              <div className="flex-shrink-0 mt-1">
                {getStatusIcon(transaction.status)}
              </div>

              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2 mb-1">
                  <span className="text-white/80 font-medium text-sm">
                    #{transaction.transaction_id}
                  </span>
                  <Badge className={`text-xs ${getStatusColor(transaction.status)}`}>
                    {transaction.status}
                  </Badge>
                </div>
                
                <p className="text-white/60 text-sm truncate mb-2">
                  {transaction.query}
                </p>
                
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-3 text-xs text-white/40">
                    <div className="flex items-center gap-1">
                      <Database className="w-3 h-3" />
                      {transaction.database_name}
                    </div>
                    <span>
                      {new Date(transaction.created_date).toLocaleTimeString()}
                    </span>
                  </div>
                  <span className="text-xs text-white/40">
                    {transaction.duration}ms
                  </span>
                </div>
              </div>

              {/* Timeline line */}
              {index < filteredTransactions.length - 1 && (
                <div className="absolute left-2 top-12 w-px h-4 bg-gradient-to-b from-white/20 to-transparent"></div>
              )}
            </div>
          </div>
        ))}
        
        {filteredTransactions.length === 0 && (
          <div className="text-center py-8">
            <Clock className="w-8 h-8 text-white/20 mx-auto mb-2" />
            <p className="text-white/40 text-sm">No transactions found</p>
          </div>
        )}
      </div>
    </div>
  );
}