
import React, { useState } from "react";
import { MonitoringSession } from "@/api/entities";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Badge } from "@/components/ui/badge";
import { Video, Square, Play, Clock, Sparkles } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { createPageUrl } from "@/utils";

export default function RecordingControls({ isRecording, onStartRecording, onStopRecording }) {
  const [showStartDialog, setShowStartDialog] = useState(false);
  const [sessionName, setSessionName] = useState("");
  const [sessionDescription, setSessionDescription] = useState("");
  const [recordingStartTime, setRecordingStartTime] = useState(null);
  const navigate = useNavigate();

  const handleStartRecording = async () => {
    if (!sessionName.trim()) return;
    
    const startTime = new Date();
    setRecordingStartTime(startTime);
    
    // Create monitoring session
    await MonitoringSession.create({
      session_name: sessionName,
      description: sessionDescription,
      start_time: startTime.toISOString(),
      status: "recording",
      metrics_data: [],
      transactions_data: []
    });
    
    onStartRecording();
    setShowStartDialog(false);
    setSessionName("");
    setSessionDescription("");
  };

  const handleStopRecording = async () => {
    const recordedData = onStopRecording();
    const endTime = new Date();
    const duration = Math.round((endTime - recordingStartTime) / 1000 / 60); // minutes
    
    // Find the current session and update it
    const sessions = await MonitoringSession.list("-created_date", 1);
    if (sessions.length > 0) {
      const currentSession = sessions[0];
      await MonitoringSession.update(currentSession.id, {
        end_time: endTime.toISOString(),
        status: "completed",
        metrics_data: recordedData.metrics,
        transactions_data: recordedData.transactions,
        duration_minutes: duration
      });
    }
    
    setRecordingStartTime(null);
  };

  const formatRecordingTime = () => {
    if (!recordingStartTime) return "00:00";
    const now = new Date();
    const diff = Math.floor((now - recordingStartTime) / 1000);
    const minutes = Math.floor(diff / 60);
    const seconds = diff % 60;
    return `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
  };

  return (
    <>
      <div className="glass-morphism rounded-2xl p-6 bg-gradient-to-br from-purple-500/10 to-pink-500/10 border border-purple-500/20 max-w-2xl mx-auto">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-4">
            <div className="flex items-center gap-2">
              {isRecording ? (
                <div className="w-3 h-3 bg-red-500 rounded-full animate-pulse"></div>
              ) : (
                <div className="w-3 h-3 bg-gray-400 rounded-full"></div>
              )}
              <span className="text-white/80 text-sm font-medium">
                {isRecording ? "레코딩 중" : "대기 중"}
              </span>
            </div>
            
            {isRecording && (
              <div className="flex items-center gap-2">
                <Clock className="w-4 h-4 text-white/60" />
                <span className="text-white/60 text-sm font-mono">
                  {formatRecordingTime()}
                </span>
              </div>
            )}
          </div>

          <div className="flex items-center gap-3">
            <Button
              onClick={() => navigate(createPageUrl("Sessions"))}
              className="bg-white/10 text-white border border-white/20 hover:bg-white/20 hover:border-white/30 transition-all duration-200 backdrop-blur-sm"
            >
              <Sparkles className="w-4 h-4 mr-2" />
              세션 관리
            </Button>
            
            {!isRecording ? (
              <Button
                onClick={() => setShowStartDialog(true)}
                className="bg-red-500/20 text-red-400 border border-red-500/30 hover:bg-red-500/30"
              >
                <Video className="w-4 h-4 mr-2" />
                레코딩 시작
              </Button>
            ) : (
              <Button
                onClick={handleStopRecording}
                className="bg-gray-500/20 text-gray-300 border border-gray-500/30 hover:bg-gray-500/30"
              >
                <Square className="w-4 h-4 mr-2" />
                레코딩 중지
              </Button>
            )}
          </div>
        </div>

        {isRecording && (
          <div className="mt-4 pt-4 border-t border-white/10">
            <p className="text-white/60 text-sm">
              📹 모니터링 데이터를 실시간으로 레코딩하고 있습니다. AI 분석을 위해 중지 후 세션을 저장하세요.
            </p>
          </div>
        )}
      </div>

      <Dialog open={showStartDialog} onOpenChange={setShowStartDialog}>
        <DialogContent className="glass-morphism border border-white/20 bg-gradient-to-br from-slate-900/90 to-slate-800/90">
          <DialogHeader>
            <DialogTitle className="text-white flex items-center gap-2">
              <Video className="w-5 h-5 text-red-400" />
              레코딩 세션 시작
            </DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <div>
              <label className="text-white/80 text-sm font-medium mb-2 block">
                세션 이름 *
              </label>
              <Input
                value={sessionName}
                onChange={(e) => setSessionName(e.target.value)}
                placeholder="예: 피크 시간대 성능 모니터링"
                className="glass-morphism border-white/20 text-white placeholder:text-white/40"
              />
            </div>
            <div>
              <label className="text-white/80 text-sm font-medium mb-2 block">
                설명 (선택사항)
              </label>
              <Textarea
                value={sessionDescription}
                onChange={(e) => setSessionDescription(e.target.value)}
                placeholder="이 레코딩 세션의 목적과 컨텍스트를 설명해주세요..."
                className="glass-morphism border-white/20 text-white placeholder:text-white/40 h-20"
              />
            </div>
            <div className="flex justify-end gap-3 pt-4">
              <Button
                onClick={() => setShowStartDialog(false)}
                className="bg-white/10 text-white border border-white/20 hover:bg-white/20 hover:border-white/30 transition-all duration-200 backdrop-blur-sm"
              >
                취소
              </Button>
              <Button
                onClick={handleStartRecording}
                disabled={!sessionName.trim()}
                className="bg-red-500/20 text-red-400 border border-red-500/30 hover:bg-red-500/30 disabled:opacity-50"
              >
                <Play className="w-4 h-4 mr-2" />
                레코딩 시작
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>
    </>
  );
}
