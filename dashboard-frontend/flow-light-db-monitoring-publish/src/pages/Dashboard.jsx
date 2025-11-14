
import React, { useState, useEffect } from "react";

import MetricsGrid from "../components/dashboard/MetricsGrid";
import TransactionTimeline from "../components/dashboard/TransactionTimeline";
import DeadlocksCard from "../components/dashboard/DeadlocksCard";
import LongRunningTransactions from "../components/dashboard/LongRunningTransactions";
import LiveMetricsChart from "../components/dashboard/LiveMetricsChart";
import RecordingControls from "../components/dashboard/RecordingControls";

// WebSocket hook for real-time data (disabled for now due to import path issues)
// import { useRealTimeMetrics } from "../hooks/useRealTimeMetrics";

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
  const [realLongRunningTransactions, setRealLongRunningTransactions] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isRecording, setIsRecording] = useState(false);
  const [recordedData, setRecordedData] = useState({ metrics: [], transactions: [] });
  const [wsConnectionStatus, setWsConnectionStatus] = useState('disconnected');

  useEffect(() => {
    // Initialize with some historical data
    const initialHistory = Array.from({ length: 20 }, (_, i) => ({
      ...generateMockMetrics(),
      timestamp: new Date(Date.now() - (20 - i) * 30000)
    }));
    setMetricsHistory(initialHistory);
    setIsLoading(false);

    // WebSocket connection for real-time Long-running Transaction data
    const wsUrl = 'ws://kube-db-mon-controlplane.bitgaram.info/ws';
    let ws = null;
    
    const connectWebSocket = () => {
      try {
        setWsConnectionStatus('connecting');
        console.log('🔄 WebSocket 연결 시도:', wsUrl);
        
        ws = new WebSocket(wsUrl);
        
        ws.onopen = () => {
          console.log('✅ WebSocket 연결됨');
          setWsConnectionStatus('connected');
        };
        
        ws.onmessage = (event) => {
          try {
            const message = JSON.parse(event.data);
            console.log('📥 WebSocket 메시지 수신:', message);
            
            // Long-running Transaction 이벤트 처리
            if (message.type === 'long_running_transaction') {
              const transactionData = {
                id: message.data?.transaction_id || `lr_${Date.now()}`,
                transaction_id: message.data?.transaction_id || 'N/A',
                query: message.data?.sql_text || 'Long-running query in progress...',
                duration: message.data?.duration_ms || 0,
                status: message.data?.status || 'active',
                database_name: message.data?.database_name || 'postgresql',
                user_session: message.data?.connection_id || 'unknown',
                created_date: new Date()
              };
              
              console.log('🐌 Long-running Transaction 추가:', transactionData);
              setRealLongRunningTransactions(prev => {
                // 중복 방지를 위해 transaction_id 체크
                const filtered = prev.filter(t => t.transaction_id !== transactionData.transaction_id);
                return [transactionData, ...filtered].slice(0, 10); // 최대 10개 유지
              });
            }
          } catch (error) {
            console.warn('WebSocket 메시지 파싱 실패:', error, event.data);
          }
        };
        
        ws.onerror = (error) => {
          console.error('❌ WebSocket 오류:', error);
          setWsConnectionStatus('error');
        };
        
        ws.onclose = () => {
          console.log('🔌 WebSocket 연결 종료');
          setWsConnectionStatus('disconnected');
          // 3초 후 재연결 시도
          setTimeout(connectWebSocket, 3000);
        };
        
      } catch (error) {
        console.error('WebSocket 생성 실패:', error);
        setWsConnectionStatus('error');
      }
    };
    
    connectWebSocket();

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
      if (ws) {
        ws.close();
      }
    };
  }, [isRecording]); // Added isRecording to dependency array

  const deadlocks = transactions.filter((t) => t.status === 'failed').slice(0, 5);
  const longRunning = transactions.filter((t) => t.status === 'active' && t.duration > 2000).slice(0, 8);
  
  // 실제 WebSocket 데이터가 있으면 우선 사용, 없으면 Mock 데이터 사용
  const displayLongRunning = realLongRunningTransactions.length > 0 ? realLongRunningTransactions : longRunning;

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
          <LongRunningTransactions transactions={displayLongRunning} />
          <TransactionTimeline transactions={transactions.slice(0, 10)} />
        </div>
        
        {/* WebSocket 연결 상태 표시 */}
        <div className="fixed bottom-4 right-4 z-50">
          <div className={`glass-morphism rounded-lg px-3 py-2 text-xs flex items-center gap-2 ${
            wsConnectionStatus === 'connected' ? 'border-green-500/30 bg-green-500/10' :
            wsConnectionStatus === 'connecting' ? 'border-yellow-500/30 bg-yellow-500/10' :
            'border-red-500/30 bg-red-500/10'
          }`}>
            <div className={`w-2 h-2 rounded-full ${
              wsConnectionStatus === 'connected' ? 'bg-green-400 animate-pulse' :
              wsConnectionStatus === 'connecting' ? 'bg-yellow-400 animate-spin' :
              'bg-red-400'
            }`}></div>
            <span className="text-white/80">
              WebSocket: {wsConnectionStatus === 'connected' ? '연결됨' : 
                         wsConnectionStatus === 'connecting' ? '연결 중...' : '연결 안됨'}
              {realLongRunningTransactions.length > 0 && (
                <span className="ml-2 text-green-400">({realLongRunningTransactions.length}개 수신)</span>
              )}
            </span>
          </div>
        </div>
      </div>
    </div>);

}