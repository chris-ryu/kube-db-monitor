'use client'

import { useState, useEffect } from 'react'
import { QueryFlowAnimation } from '@/components/QueryFlowAnimation'
import { useRealTimeMetrics } from '@/hooks/useRealTimeMetrics'

export default function DebugPage() {
  const [mockData, setMockData] = useState(false)
  const { metrics, isConnected, connectionStatus, aggregatedMetrics } = useRealTimeMetrics({
    // 모든 이벤트 허용
  })

  // Mock data for testing animations
  useEffect(() => {
    if (mockData) {
      const interval = setInterval(() => {
        // Simulate WebSocket data by manually triggering events
        console.log('🎭 Mock data mode - simulating query')
        
        // Create a custom event to simulate WebSocket message
        const mockQuery = {
          timestamp: new Date().toISOString(),
          pod_name: `mock-pod-${Math.random().toString(36).substr(2, 5)}`,
          event_type: 'query_execution',
          data: {
            query_id: `mock_${Date.now()}`,
            sql_pattern: 'SELECT * FROM users WHERE id = ?',
            sql_type: 'SELECT',
            execution_time_ms: Math.floor(Math.random() * 100) + 10,
            status: Math.random() > 0.8 ? 'ERROR' : 'SUCCESS'
          },
          metrics: {
            connection_pool_active: Math.floor(Math.random() * 10) + 5,
            connection_pool_max: 20
          }
        }
        
        // Dispatch custom event that the hook can listen to
        window.dispatchEvent(new CustomEvent('mockWebSocketMessage', { 
          detail: { data: mockQuery } 
        }))
      }, 1000)
      
      return () => clearInterval(interval)
    }
  }, [mockData])

  return (
    <div className="min-h-screen bg-gray-900 text-white p-8">
      <div className="max-w-6xl mx-auto">
        <h1 className="text-3xl font-bold mb-6">🔍 Query Flow Animation Debug</h1>
        
        {/* Debug Info */}
        <div className="bg-gray-800 rounded-lg p-4 mb-6 border border-gray-600">
          <h2 className="text-xl font-semibold mb-3">연결 정보</h2>
          <div className="grid grid-cols-2 gap-4 text-sm">
            <div>
              <strong>연결 상태:</strong> 
              <span className={`ml-2 px-2 py-1 rounded ${
                isConnected ? 'bg-green-600' : 'bg-red-600'
              }`}>
                {connectionStatus}
              </span>
            </div>
            <div><strong>메트릭 수:</strong> {metrics.length}</div>
            <div><strong>QPS:</strong> {aggregatedMetrics.qps}</div>
            <div><strong>활성 커넥션:</strong> {aggregatedMetrics.active_connections}</div>
          </div>
        </div>

        {/* Controls */}
        <div className="bg-gray-800 rounded-lg p-4 mb-6 border border-gray-600">
          <h2 className="text-xl font-semibold mb-3">테스트 컨트롤</h2>
          <button
            onClick={() => setMockData(!mockData)}
            className={`px-4 py-2 rounded ${
              mockData ? 'bg-red-600 hover:bg-red-700' : 'bg-blue-600 hover:bg-blue-700'
            }`}
          >
            {mockData ? 'Mock 데이터 끄기' : 'Mock 데이터 켜기'}
          </button>
        </div>

        {/* Recent Metrics */}
        <div className="bg-gray-800 rounded-lg p-4 mb-6 border border-gray-600">
          <h2 className="text-xl font-semibold mb-3">최근 메트릭 (최대 5개)</h2>
          {metrics.length === 0 ? (
            <p className="text-gray-400">메트릭 없음 - WebSocket 연결을 확인하세요</p>
          ) : (
            <div className="space-y-2">
              {metrics.slice(0, 5).map((metric, index) => (
                <div key={index} className="bg-gray-700 p-2 rounded text-xs">
                  <div><strong>시간:</strong> {metric.timestamp}</div>
                  <div><strong>이벤트:</strong> {metric.event_type}</div>
                  {metric.data && (
                    <div><strong>쿼리:</strong> {metric.data.query_id} - {metric.data.execution_time_ms}ms</div>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Query Flow Animation */}
        <div className="bg-gray-800 rounded-lg p-4 border border-gray-600">
          <h2 className="text-xl font-semibold mb-3">Query Flow Animation</h2>
          <QueryFlowAnimation className="w-full" maxDisplayedQueries={5} />
        </div>

        {/* Raw WebSocket Test */}
        <div className="bg-gray-800 rounded-lg p-4 mt-6 border border-gray-600">
          <h2 className="text-xl font-semibold mb-3">Raw WebSocket Test</h2>
          <WebSocketTest />
        </div>
      </div>
    </div>
  )
}

function WebSocketTest() {
  const [messages, setMessages] = useState<string[]>([])
  const [wsStatus, setWsStatus] = useState<string>('disconnected')

  useEffect(() => {
    const ws = new WebSocket('ws://localhost:8080/ws')
    
    ws.onopen = () => {
      setWsStatus('connected')
      console.log('✅ Raw WebSocket connected')
    }
    
    ws.onmessage = (event) => {
      console.log('📨 Raw message received:', event.data)
      setMessages(prev => [event.data, ...prev].slice(0, 5))
    }
    
    ws.onclose = () => {
      setWsStatus('disconnected')
      console.log('❌ Raw WebSocket disconnected')
    }
    
    ws.onerror = (error) => {
      setWsStatus('error')
      console.error('❌ Raw WebSocket error:', error)
    }
    
    return () => {
      ws.close()
    }
  }, [])

  return (
    <div>
      <div className="mb-3">
        <strong>Raw WebSocket Status:</strong> 
        <span className={`ml-2 px-2 py-1 rounded text-sm ${
          wsStatus === 'connected' ? 'bg-green-600' : 
          wsStatus === 'error' ? 'bg-red-600' : 'bg-yellow-600'
        }`}>
          {wsStatus}
        </span>
      </div>
      
      <div>
        <strong>Raw Messages:</strong>
        {messages.length === 0 ? (
          <p className="text-gray-400 text-sm">메시지 없음</p>
        ) : (
          <div className="mt-2 space-y-1">
            {messages.map((message, index) => (
              <div key={index} className="bg-gray-700 p-2 rounded text-xs font-mono">
                {message}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}