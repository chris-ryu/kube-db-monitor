
import React, { useState, useEffect } from "react";

import MetricsGrid from "../components/dashboard/MetricsGrid";
import TransactionTimeline from "../components/dashboard/TransactionTimeline";
import DeadlocksCard from "../components/dashboard/DeadlocksCard";
import LongRunningTransactions from "../components/dashboard/LongRunningTransactions";
import LiveMetricsChart from "../components/dashboard/LiveMetricsChart";
import RecordingControls from "../components/dashboard/RecordingControls";

// Mock data generator for real-time simulation
const generateMockMetrics = () => ({
  qps: Math.floor(Math.random() * 1000) + 500,
  tps: Math.floor(Math.random() * 800) + 200,
  avgLatency: Math.floor(Math.random() * 50) + 10,
  activeConnections: Math.floor(Math.random() * 100) + 50,
  activeTransactions: Math.floor(Math.random() * 200) + 100,
  errorRate: Math.random() * 5,
  connectionPool: Math.floor(Math.random() * 80) + 20, // 20-100 range for connection pool usage
  timestamp: new Date()
});

const generateMockTransactions = () => {
  const queries = [
  "SELECT * FROM users WHERE status = 'active'",
  "UPDATE orders SET status = 'completed' WHERE id IN (...)",
  "INSERT INTO audit_log (action, user_id, timestamp) VALUES (...)",
  "DELETE FROM temp_data WHERE created_at < NOW() - INTERVAL 1 DAY",
  "SELECT COUNT(*) FROM products p JOIN categories c ON p.category_id = c.id"];


  const databases = ["prod_db", "analytics_db", "user_db", "inventory_db"];

  return Array.from({ length: 15 }, (_, i) => ({
    id: `txn_${Date.now()}_${i}`,
    transaction_id: `TXN${1000 + i}`,
    query: queries[Math.floor(Math.random() * queries.length)],
    duration: Math.floor(Math.random() * 5000) + 100,
    status: Math.random() > 0.9 ? 'failed' : Math.random() > 0.7 ? 'completed' : 'active',
    database_name: databases[Math.floor(Math.random() * databases.length)],
    user_session: `session_${Math.floor(Math.random() * 1000)}`,
    created_date: new Date(Date.now() - Math.random() * 86400000)
  }));
};

export default function Dashboard() {
  const [metrics, setMetrics] = useState(generateMockMetrics());
  const [metricsHistory, setMetricsHistory] = useState([]);
  const [transactions, setTransactions] = useState(generateMockTransactions());
  const [isLoading, setIsLoading] = useState(true);
  const [isRecording, setIsRecording] = useState(false);
  const [recordedData, setRecordedData] = useState({ metrics: [], transactions: [] });

  useEffect(() => {
    // Initialize with some historical data
    const initialHistory = Array.from({ length: 20 }, (_, i) => ({
      ...generateMockMetrics(),
      timestamp: new Date(Date.now() - (20 - i) * 30000)
    }));
    setMetricsHistory(initialHistory);
    setIsLoading(false);

    // Update metrics every 3 seconds
    const metricsInterval = setInterval(() => {
      const newMetrics = generateMockMetrics();
      setMetrics(newMetrics);
      setMetricsHistory((prev) => [...prev.slice(-19), newMetrics]);

      // Record data if recording is active
      if (isRecording) {
        setRecordedData((prev) => ({
          metrics: [...prev.metrics, newMetrics],
          transactions: prev.transactions
        }));
      }
    }, 3000);

    // Update transactions every 10 seconds
    const transactionsInterval = setInterval(() => {
      const newTransactions = generateMockTransactions();
      setTransactions(newTransactions);

      // Record transactions if recording is active
      if (isRecording) {
        setRecordedData((prev) => ({
          metrics: prev.metrics,
          transactions: [...prev.transactions, ...newTransactions]
        }));
      }
    }, 10000);

    return () => {
      clearInterval(metricsInterval);
      clearInterval(transactionsInterval);
    };
  }, [isRecording]); // Added isRecording to dependency array

  const deadlocks = transactions.filter((t) => t.status === 'failed').slice(0, 5);
  const longRunning = transactions.filter((t) => t.status === 'active' && t.duration > 2000).slice(0, 8);

  const handleStartRecording = () => {
    setIsRecording(true);
    setRecordedData({ metrics: [], transactions: [] }); // Reset recorded data on start
  };

  const handleStopRecording = () => {
    setIsRecording(false);
    // In a real application, you might send recordedData to a backend for AI analysis here
    // For this mock, we'll just log it or return it.
    console.log("Recorded data for AI analysis:", recordedData);
    return recordedData;
  };

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="glass-morphism rounded-2xl p-8">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-white/60 mx-auto"></div>
          <p className="text-white/60 mt-4 text-center">대시보드 로딩 중...</p>
        </div>
      </div>);

  }

  return (
    <div className="min-h-screen p-6">
      <div className="max-w-7xl mx-auto space-y-6">
        {/* Header with Recording Controls */}
        <div className="text-center mb-8">
          <h1 className="text-4xl font-bold text-white mb-2">FlowLight DB Monitor

          </h1>
          <p className="text-white/60 text-lg mb-4">
            실시간 데이터베이스 성능 인사이트
          </p>
          <RecordingControls
            isRecording={isRecording}
            onStartRecording={handleStartRecording}
            onStopRecording={handleStopRecording} />

        </div>

        {/* Main Metrics Grid */}
        <MetricsGrid metrics={metrics} />

        {/* Charts Section */}
        <div className="grid lg:grid-cols-3 gap-6">
          <div className="lg:col-span-2">
            <LiveMetricsChart data={metricsHistory} />
          </div>
          <div className="space-y-6">
            <DeadlocksCard deadlocks={deadlocks} />
          </div>
        </div>

        {/* Transactions Section */}
        <div className="grid lg:grid-cols-2 gap-6">
          <LongRunningTransactions transactions={longRunning} />
          <TransactionTimeline transactions={transactions.slice(0, 10)} />
        </div>
      </div>
    </div>);

}