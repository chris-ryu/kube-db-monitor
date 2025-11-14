'use client'

import React, { useState, useEffect } from "react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { 
  Video, 
  Play, 
  Clock, 
  CheckCircle,
  Eye,
  Trash2
} from "lucide-react";

interface MonitoringSession {
  id: string;
  session_name: string;
  description?: string;
  start_time: string;
  end_time?: string;
  status: 'recording' | 'completed';
  duration_minutes: number;
  metrics_data: any[];
  transactions_data: any[];
}

export default function Sessions() {
  const [sessions, setSessions] = useState<MonitoringSession[]>([]);
  const [selectedSession, setSelectedSession] = useState<MonitoringSession | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    loadSessions();
  }, []);

  const loadSessions = async () => {
    setIsLoading(true);
    try {
      // Try Control Plane API first
      const controlPlaneUrl = process.env.NEXT_PUBLIC_CONTROL_PLANE_URL || 'http://localhost:8081';
      const response = await fetch(`${controlPlaneUrl}/api/sessions`);
      
      if (response.ok) {
        const data = await response.json();
        setSessions(data || []);
      } else {
        // 임시로 목업 데이터 사용
        const mockSessions: MonitoringSession[] = [
          {
            id: 'session-1',
            session_name: '피크 시간대 성능 모니터링',
            description: '오후 2시-4시 트래픽 집중 시간대 모니터링 세션',
            start_time: new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString(),
            end_time: new Date(Date.now() - 30 * 60 * 1000).toISOString(),
            status: 'completed',
            duration_minutes: 90,
            metrics_data: Array.from({length: 180}, (_, i) => ({
              timestamp: new Date(Date.now() - (180-i) * 30000),
              qps: Math.floor(Math.random() * 1000) + 500,
              tps: Math.floor(Math.random() * 800) + 200,
              avgLatency: Math.floor(Math.random() * 50) + 10,
              errorRate: Math.random() * 2
            })),
            transactions_data: Array.from({length: 45}, (_, i) => ({
              id: `tx-${i}`,
              transaction_id: `TXN${1000 + i}`,
              duration: Math.floor(Math.random() * 3000) + 100,
              status: Math.random() > 0.9 ? 'failed' : 'completed'
            }))
          },
          {
            id: 'session-2', 
            session_name: '데드락 발생 시나리오 분석',
            description: '동시 트랜잭션에서 발생한 데드락 상황 분석',
            start_time: new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString(),
            end_time: new Date(Date.now() - 23 * 60 * 60 * 1000).toISOString(),
            status: 'completed',
            duration_minutes: 60,
            metrics_data: Array.from({length: 120}, () => ({
              qps: Math.floor(Math.random() * 500) + 200,
              tps: Math.floor(Math.random() * 400) + 100,
              avgLatency: Math.floor(Math.random() * 100) + 20,
              errorRate: Math.random() * 5
            })),
            transactions_data: Array.from({length: 30}, (_, i) => ({
              id: `tx-dl-${i}`,
              transaction_id: `DLTX${2000 + i}`,
              duration: Math.floor(Math.random() * 5000) + 1000,
              status: Math.random() > 0.7 ? 'failed' : 'completed'
            }))
          }
        ];
        setSessions(mockSessions);
      }
    } catch (error) {
      console.error('세션 로딩 실패:', error);
      setSessions([]);
    }
    setIsLoading(false);
  };

  const getStatusIcon = (status: string) => {
    switch (status) {
      case "recording":
        return <Play className="w-4 h-4 text-red-400 animate-pulse" />;
      case "completed":
        return <CheckCircle className="w-4 h-4 text-green-400" />;
      default:
        return <Clock className="w-4 h-4 text-gray-400" />;
    }
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case "recording":
        return "bg-red-500/20 text-red-400 border-red-500/30";
      case "completed":
        return "bg-green-500/20 text-green-400 border-green-500/30";
      default:
        return "bg-gray-500/20 text-gray-400 border-gray-500/30";
    }
  };

  const deleteSession = async (sessionId: string) => {
    try {
      const controlPlaneUrl = process.env.NEXT_PUBLIC_CONTROL_PLANE_URL || 'http://localhost:8081';
      const response = await fetch(`${controlPlaneUrl}/api/sessions/${sessionId}`, {
        method: 'DELETE'
      });
      if (response.ok) {
        await loadSessions();
      } else {
        // 임시로 로컬에서 삭제
        setSessions(prev => prev.filter(s => s.id !== sessionId));
      }
    } catch (error) {
      console.error('세션 삭제 실패:', error);
      // 임시로 로컬에서 삭제
      setSessions(prev => prev.filter(s => s.id !== sessionId));
    }
  };

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleDateString('ko-KR', {
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    });
  };

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="glass-morphism rounded-2xl p-8">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-white/60 mx-auto"></div>
          <p className="text-white/60 mt-4 text-center">세션 목록 로딩 중...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen p-6">
      <div className="max-w-6xl mx-auto">
        <div className="text-center mb-8">
          <h1 className="text-4xl font-bold text-white mb-2">
            모니터링 세션 관리
          </h1>
          <p className="text-white/60 text-lg">
            레코딩된 세션들을 관리하고 분석하세요
          </p>
        </div>

        <div className="grid gap-6">
          {sessions.map((session) => (
            <div
              key={session.id}
              className="glass-morphism rounded-2xl p-6 bg-gradient-to-br from-white/5 to-white/10 border border-white/20 hover:border-white/30 transition-all duration-300"
            >
              <div className="flex items-start justify-between">
                <div className="flex-1">
                  <div className="flex items-center gap-3 mb-2">
                    {getStatusIcon(session.status)}
                    <h3 className="text-xl font-bold text-white">{session.session_name}</h3>
                    <Badge className={`${getStatusColor(session.status)} text-xs`}>
                      {session.status === "recording" && "레코딩 중"}
                      {session.status === "completed" && "완료됨"}
                    </Badge>
                  </div>
                  
                  {session.description && (
                    <p className="text-white/60 mb-3">{session.description}</p>
                  )}
                  
                  <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
                    <div>
                      <p className="text-white/40">시작 시간</p>
                      <p className="text-white/80">
                        {formatDate(session.start_time)}
                      </p>
                    </div>
                    <div>
                      <p className="text-white/40">지속 시간</p>
                      <p className="text-white/80">
                        {session.duration_minutes || 0}분
                      </p>
                    </div>
                    <div>
                      <p className="text-white/40">메트릭 데이터</p>
                      <p className="text-white/80">
                        {(session.metrics_data || []).length}개
                      </p>
                    </div>
                    <div>
                      <p className="text-white/40">트랜잭션</p>
                      <p className="text-white/80">
                        {(session.transactions_data || []).length}개
                      </p>
                    </div>
                  </div>
                </div>

                <div className="flex items-center gap-2 ml-4">
                  <Button
                    onClick={() => setSelectedSession(session)}
                    className="bg-blue-500/20 text-blue-400 border border-blue-500/30 hover:bg-blue-500/30 transition-all duration-200"
                  >
                    <Eye className="w-4 h-4" />
                  </Button>
                  
                  <Button
                    onClick={() => deleteSession(session.id)}
                    className="bg-red-500/20 text-red-400 border border-red-500/30 hover:bg-red-500/30 transition-all duration-200"
                  >
                    <Trash2 className="w-4 h-4" />
                  </Button>
                </div>
              </div>
            </div>
          ))}

          {sessions.length === 0 && (
            <div className="text-center py-12">
              <Video className="w-16 h-16 text-white/20 mx-auto mb-4" />
              <h3 className="text-xl font-medium text-white/60 mb-2">
                레코딩된 세션이 없습니다
              </h3>
              <p className="text-white/40">
                대시보드에서 모니터링을 시작해보세요
              </p>
            </div>
          )}
        </div>
      </div>

      {/* Session Detail Dialog */}
      <Dialog open={!!selectedSession} onOpenChange={() => setSelectedSession(null)}>
        <DialogContent className="glass-morphism border border-white/20 bg-gradient-to-br from-slate-900/90 to-slate-800/90 max-w-4xl max-h-[80vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle className="text-white flex items-center gap-2">
              <Video className="w-5 h-5 text-purple-400" />
              {selectedSession?.session_name}
            </DialogTitle>
          </DialogHeader>
          
          {selectedSession && (
            <div className="space-y-6">
              {/* Basic Info */}
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <p className="text-white/60 text-sm">상태</p>
                  <Badge className={`${getStatusColor(selectedSession.status)} mt-1`}>
                    {selectedSession.status === "recording" ? "레코딩 중" : "완료됨"}
                  </Badge>
                </div>
                <div>
                  <p className="text-white/60 text-sm">지속 시간</p>
                  <p className="text-white font-medium">{selectedSession.duration_minutes}분</p>
                </div>
              </div>

              {/* Description */}
              {selectedSession.description && (
                <div>
                  <p className="text-white/60 text-sm mb-2">설명</p>
                  <p className="text-white/80">{selectedSession.description}</p>
                </div>
              )}

              {/* Data Summary */}
              <div className="grid grid-cols-2 gap-4">
                <div className="glass-morphism rounded-lg p-4 bg-black/20 border border-white/10">
                  <h4 className="font-medium text-white mb-2">메트릭 데이터</h4>
                  <p className="text-white/60 text-sm">
                    {(selectedSession.metrics_data || []).length}개 데이터 포인트
                  </p>
                  {selectedSession.metrics_data && selectedSession.metrics_data.length > 0 && (
                    <div className="mt-2 text-xs text-white/40">
                      <p>평균 QPS: {Math.round(selectedSession.metrics_data.reduce((sum: number, m: any) => sum + (m.qps || 0), 0) / selectedSession.metrics_data.length)}</p>
                      <p>평균 지연시간: {Math.round(selectedSession.metrics_data.reduce((sum: number, m: any) => sum + (m.avgLatency || 0), 0) / selectedSession.metrics_data.length)}ms</p>
                    </div>
                  )}
                </div>
                <div className="glass-morphism rounded-lg p-4 bg-black/20 border border-white/10">
                  <h4 className="font-medium text-white mb-2">트랜잭션 데이터</h4>
                  <p className="text-white/60 text-sm">
                    {(selectedSession.transactions_data || []).length}개 트랜잭션
                  </p>
                  {selectedSession.transactions_data && selectedSession.transactions_data.length > 0 && (
                    <div className="mt-2 text-xs text-white/40">
                      <p>실패한 트랜잭션: {selectedSession.transactions_data.filter((t: any) => t.status === 'failed').length}개</p>
                      <p>장시간 실행: {selectedSession.transactions_data.filter((t: any) => t.duration > 2000).length}개</p>
                    </div>
                  )}
                </div>
              </div>
            </div>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}