import React from "react";
import { 
  Activity, 
  Database, 
  Clock, 
  Users, 
  Zap, 
  AlertTriangle 
} from "lucide-react";

const MetricCard = ({ title, value, unit, icon: Icon, color, delay = 0 }) => {
  const getStatusColor = () => {
    if (title === "Error Rate" && value > 2) return "from-red-500/20 to-pink-500/20 border-red-500/30";
    if (title === "Avg Latency" && value > 30) return "from-orange-500/20 to-yellow-500/20 border-orange-500/30";
    return `${color} border-white/20`;
  };

  return (
    <div className="group transform hover:-translate-y-1 hover:scale-105 transition-all duration-300">
      <div className={`glass-morphism rounded-2xl p-6 bg-gradient-to-br ${getStatusColor()} transition-all duration-300 hover:shadow-2xl hover:shadow-purple-500/10`}>
        <div className="flex items-start justify-between mb-4">
          <div className={`p-3 rounded-xl bg-gradient-to-br ${color} backdrop-blur-sm`}>
            <Icon className="w-6 h-6 text-white" />
          </div>
        </div>
        
        <div className="space-y-2">
          <h3 className="text-white/80 text-sm font-medium">{title}</h3>
          <div className="flex items-baseline gap-2">
            <span className="text-3xl font-bold text-white">
              {typeof value === 'number' ? value.toFixed(title === "Error Rate" ? 2 : 0) : value}
            </span>
            <span className="text-white/60 text-sm">{unit}</span>
          </div>
        </div>
      </div>
    </div>
  );
};

export default function MetricsGrid({ metrics }) {
  const metricsConfig = [
    {
      title: "QPS",
      value: metrics.qps,
      unit: "queries/sec",
      icon: Activity,
      color: "from-blue-500/20 to-cyan-500/20"
    },
    {
      title: "TPS", 
      value: metrics.tps,
      unit: "trans/sec",
      icon: Zap,
      color: "from-purple-500/20 to-pink-500/20"
    },
    {
      title: "Avg Latency",
      value: metrics.avgLatency,
      unit: "ms",
      icon: Clock,
      color: "from-emerald-500/20 to-green-500/20"
    },
    {
      title: "Active Connections",
      value: metrics.activeConnections,
      unit: "connections",
      icon: Users,
      color: "from-indigo-500/20 to-blue-500/20"
    },
    {
      title: "Active Transactions",
      value: metrics.activeTransactions,
      unit: "transactions", 
      icon: Database,
      color: "from-violet-500/20 to-purple-500/20"
    },
    {
      title: "Error Rate",
      value: metrics.errorRate,
      unit: "%",
      icon: AlertTriangle,
      color: "from-orange-500/20 to-red-500/20"
    }
  ];

  return (
    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
      {metricsConfig.map((metric, index) => (
        <MetricCard
          key={metric.title}
          {...metric}
          delay={index * 0.1}
        />
      ))}
    </div>
  );
}