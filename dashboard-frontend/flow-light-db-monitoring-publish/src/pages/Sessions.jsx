
import React, { useState, useEffect } from "react";
import { MonitoringSession } from "@/api/entities";
import { InvokeLLM } from "@/api/integrations";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { 
  Video, 
  Play, 
  Clock, 
  Brain, 
  TrendingUp, 
  AlertTriangle,
  CheckCircle,
  Eye,
  Trash2,
  Sparkles
} from "lucide-react";
import { format } from "date-fns";

export default function Sessions() {
  const [sessions, setSessions] = useState([]);
  const [selectedSession, setSelectedSession] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isAnalyzing, setIsAnalyzing] = useState(false);

  useEffect(() => {
    loadSessions();
  }, []);

  const loadSessions = async () => {
    setIsLoading(true);
    const data = await MonitoringSession.list("-created_date");
    setSessions(data);
    setIsLoading(false);
  };

  const getStatusIcon = (status) => {
    switch (status) {
      case "recording":
        return <Play className="w-4 h-4 text-red-400 animate-pulse" />;
      case "completed":
        return <CheckCircle className="w-4 h-4 text-green-400" />;
      case "analyzing":
        return <Brain className="w-4 h-4 text-yellow-400 animate-spin" />;
      case "analyzed":
        return <Sparkles className="w-4 h-4 text-purple-400" />;
      default:
        return <Clock className="w-4 h-4 text-gray-400" />;
    }
  };

  const getStatusColor = (status) => {
    switch (status) {
      case "recording":
        return "bg-red-500/20 text-red-400 border-red-500/30";
      case "completed":
        return "bg-green-500/20 text-green-400 border-green-500/30";
      case "analyzing":
        return "bg-yellow-500/20 text-yellow-400 border-yellow-500/30";
      case "analyzed":
        return "bg-purple-500/20 text-purple-400 border-purple-500/30";
      default:
        return "bg-gray-500/20 text-gray-400 border-gray-500/30";
    }
  };

  const requestAIAnalysis = async (session) => {
    setIsAnalyzing(true);
    
    // Update session status to analyzing
    await MonitoringSession.update(session.id, { status: "analyzing" });
    await loadSessions();

    try {
      // Prepare data for AI analysis
      const metricsData = session.metrics_data || [];
      const transactionsData = session.transactions_data || [];
      
      const analysisPrompt = `
다음은 데이터베이스 모니터링 세션 데이터입니다. 성능 문제점과 개선 방안을 분석해주세요:

세션 정보:
- 이름: ${session.session_name}
- 설명: ${session.description || "설명 없음"}
- 지속시간: ${session.duration_minutes || 0}분
- 시작: ${new Date(session.start_time).toLocaleString()}
- 종료: ${session.end_time ? new Date(session.end_time).toLocaleString() : "진행 중"}

메트릭 데이터 요약:
- 총 데이터 포인트: ${metricsData.length}개
${metricsData.length > 0 ? `
- 평균 QPS: ${(metricsData.reduce((sum, m) => sum + m.qps, 0) / metricsData.length).toFixed(2)}
- 평균 TPS: ${(metricsData.reduce((sum, m) => sum + m.tps, 0) / metricsData.length).toFixed(2)}  
- 평균 지연시간: ${(metricsData.reduce((sum, m) => sum + m.avgLatency, 0) / metricsData.length).toFixed(2)}ms
- 평균 에러율: ${(metricsData.reduce((sum, m) => sum + m.errorRate, 0) / metricsData.length).toFixed(2)}%
` : ""}

트랜잭션 데이터:
- 총 트랜잭션: ${transactionsData.length}개
${transactionsData.length > 0 ? `
- 실패한 트랜잭션: ${transactionsData.filter(t => t.status === 'failed').length}개
- 장시간 실행 트랜잭션: ${transactionsData.filter(t => t.duration > 2000).length}개
` : ""}

다음 형식으로 분석 결과를 제공해주세요:
1. 전체적인 성능 요약
2. 발견된 성능 문제점들 (구체적으로)
3. 개선 권장사항 (실행 가능한)
4. 위험도 점수 (1-10점, 10이 가장 위험)
`;

      const aiResponse = await InvokeLLM({
        prompt: analysisPrompt,
        response_json_schema: {
          type: "object",
          properties: {
            summary: {
              type: "string",
              description: "전체적인 성능 요약"
            },
            performance_issues: {
              type: "array",
              items: { type: "string" },
              description: "발견된 성능 문제점들"
            },
            recommendations: {
              type: "array", 
              items: { type: "string" },
              description: "개선 권장사항들"
            },
            risk_score: {
              type: "number",
              description: "위험도 점수 (1-10)"
            }
          }
        }
      });

      // Update session with AI analysis
      await MonitoringSession.update(session.id, {
        status: "analyzed",
        ai_analysis: aiResponse
      });

      await loadSessions();
      
    } catch (error) {
      console.error("AI 분석 중 오류:", error);
      await MonitoringSession.update(session.id, { status: "completed" });
      await loadSessions();
    }
    
    setIsAnalyzing(false);
  };

  const deleteSession = async (sessionId) => {
    await MonitoringSession.delete(sessionId);
    await loadSessions();
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
            레코딩된 세션들을 관리하고 AI 분석을 요청하세요
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
                      {session.status === "analyzing" && "분석 중"}
                      {session.status === "analyzed" && "분석 완료"}
                    </Badge>
                  </div>
                  
                  {session.description && (
                    <p className="text-white/60 mb-3">{session.description}</p>
                  )}
                  
                  <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
                    <div>
                      <p className="text-white/40">시작 시간</p>
                      <p className="text-white/80">
                        {format(new Date(session.start_time), "MM/dd HH:mm")}
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

                  {session.ai_analysis && (
                    <div className="mt-4 pt-4 border-t border-white/10">
                      <div className="flex items-center gap-2 mb-2">
                        <Sparkles className="w-4 h-4 text-purple-400" />
                        <span className="text-purple-400 font-medium">AI 분석 완료</span>
                        <Badge className="bg-red-500/20 text-red-400 border-red-500/30 text-xs">
                          위험도: {session.ai_analysis.risk_score}/10
                        </Badge>
                      </div>
                      <p className="text-white/60 text-sm">
                        {session.ai_analysis.summary}
                      </p>
                    </div>
                  )}
                </div>

                <div className="flex items-center gap-2 ml-4">
                  {session.status === "completed" && !session.ai_analysis && (
                    <Button
                      onClick={() => requestAIAnalysis(session)}
                      disabled={isAnalyzing}
                      className="bg-purple-500/20 text-purple-400 border border-purple-500/30 hover:bg-purple-500/30"
                    >
                      <Brain className="w-4 h-4 mr-2" />
                      AI 분석
                    </Button>
                  )}
                  
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
                    {selectedSession.status}
                  </Badge>
                </div>
                <div>
                  <p className="text-white/60 text-sm">지속 시간</p>
                  <p className="text-white font-medium">{selectedSession.duration_minutes}분</p>
                </div>
              </div>

              {/* AI Analysis Results */}
              {selectedSession.ai_analysis && (
                <div className="space-y-4">
                  <h3 className="text-lg font-bold text-white flex items-center gap-2">
                    <Sparkles className="w-5 h-5 text-purple-400" />
                    AI 분석 결과
                  </h3>
                  
                  <div className="glass-morphism rounded-lg p-4 bg-gradient-to-br from-purple-500/10 to-pink-500/10 border border-purple-500/20">
                    <div className="flex items-center justify-between mb-3">
                      <h4 className="font-medium text-white">전체 요약</h4>
                      <Badge className="bg-red-500/20 text-red-400 border-red-500/30">
                        위험도: {selectedSession.ai_analysis.risk_score}/10
                      </Badge>
                    </div>
                    <p className="text-white/80 text-sm leading-relaxed">
                      {selectedSession.ai_analysis.summary}
                    </p>
                  </div>

                  {selectedSession.ai_analysis.performance_issues?.length > 0 && (
                    <div className="glass-morphism rounded-lg p-4 bg-gradient-to-br from-orange-500/10 to-red-500/10 border border-orange-500/20">
                      <h4 className="font-medium text-white mb-3 flex items-center gap-2">
                        <AlertTriangle className="w-4 h-4 text-orange-400" />
                        발견된 성능 문제
                      </h4>
                      <ul className="space-y-2">
                        {selectedSession.ai_analysis.performance_issues.map((issue, index) => (
                          <li key={index} className="text-white/80 text-sm flex items-start gap-2">
                            <span className="text-orange-400 mt-1">•</span>
                            {issue}
                          </li>
                        ))}
                      </ul>
                    </div>
                  )}

                  {selectedSession.ai_analysis.recommendations?.length > 0 && (
                    <div className="glass-morphism rounded-lg p-4 bg-gradient-to-br from-green-500/10 to-emerald-500/10 border border-green-500/20">
                      <h4 className="font-medium text-white mb-3 flex items-center gap-2">
                        <TrendingUp className="w-4 h-4 text-green-400" />
                        개선 권장사항
                      </h4>
                      <ul className="space-y-2">
                        {selectedSession.ai_analysis.recommendations.map((rec, index) => (
                          <li key={index} className="text-white/80 text-sm flex items-start gap-2">
                            <span className="text-green-400 mt-1">✓</span>
                            {rec}
                          </li>
                        ))}
                      </ul>
                    </div>
                  )}
                </div>
              )}

              {/* Raw Data Summary */}
              <div className="grid grid-cols-2 gap-4">
                <div className="glass-morphism rounded-lg p-4 bg-black/20 border border-white/10">
                  <h4 className="font-medium text-white mb-2">메트릭 데이터</h4>
                  <p className="text-white/60 text-sm">
                    {(selectedSession.metrics_data || []).length}개 데이터 포인트
                  </p>
                </div>
                <div className="glass-morphism rounded-lg p-4 bg-black/20 border border-white/10">
                  <h4 className="font-medium text-white mb-2">트랜잭션 데이터</h4>
                  <p className="text-white/60 text-sm">
                    {(selectedSession.transactions_data || []).length}개 트랜잭션
                  </p>
                </div>
              </div>
            </div>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}
