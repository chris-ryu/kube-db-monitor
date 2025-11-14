'use client'

import React, { useState, useEffect } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Video, Square, Play, Clock, Sparkles, Users } from "lucide-react";
import Link from "next/link";

interface RecordingControlsProps {
  isRecording: boolean;
  onStartRecording: () => void;
  onStopRecording: () => { metrics: any[], transactions: any[] };
  metrics: any[];
  transactions: any[];
}

interface MonitoringSession {
  id?: string;
  session_name: string;
  description?: string;
  start_time: string;
  end_time?: string;
  status: 'recording' | 'completed';
  duration_minutes?: number;
  metrics_data: any[];
  transactions_data: any[];
}

export default function RecordingControls({ 
  isRecording, 
  onStartRecording, 
  onStopRecording, 
  metrics = [], 
  transactions = [] 
}: RecordingControlsProps) {
  const [showStartDialog, setShowStartDialog] = useState(false);
  const [sessionName, setSessionName] = useState("");
  const [sessionDescription, setSessionDescription] = useState("");
  const [recordingStartTime, setRecordingStartTime] = useState<Date | null>(null);
  const [currentTime, setCurrentTime] = useState(new Date());

  useEffect(() => {
    if (isRecording) {
      const interval = setInterval(() => {
        setCurrentTime(new Date());
      }, 1000);
      return () => clearInterval(interval);
    }
  }, [isRecording]);

  const handleStartRecording = async () => {
    if (!sessionName.trim()) return;
    
    const startTime = new Date();
    setRecordingStartTime(startTime);
    
    try {
      // Create monitoring session via API
      const sessionData: MonitoringSession = {
        session_name: sessionName,
        description: sessionDescription,
        start_time: startTime.toISOString(),
        status: "recording",
        metrics_data: [],
        transactions_data: []
      };

      const controlPlaneUrl = process.env.NEXT_PUBLIC_CONTROL_PLANE_URL || 'http://localhost:8081';
      const response = await fetch(`${controlPlaneUrl}/api/sessions`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(sessionData),
      });

      if (response.ok) {
        console.log('✅ Session created successfully');
        onStartRecording();
        setShowStartDialog(false);
        setSessionName("");
        setSessionDescription("");
      } else {
        console.error('❌ Failed to create session:', await response.text());
        // Still start recording locally
        onStartRecording();
        setShowStartDialog(false);
        setSessionName("");
        setSessionDescription("");
      }
    } catch (error) {
      console.error('❌ Failed to create session:', error);
      // Still start recording locally
      onStartRecording();
      setShowStartDialog(false);
      setSessionName("");
      setSessionDescription("");
    }
  };

  const handleStopRecording = async () => {
    const recordedData = onStopRecording();
    const endTime = new Date();
    const duration = recordingStartTime ? Math.round((endTime.getTime() - recordingStartTime.getTime()) / 1000 / 60) : 0;
    
    try {
      // Find and update the current session
      const controlPlaneUrl = process.env.NEXT_PUBLIC_CONTROL_PLANE_URL || 'http://localhost:8081';
      const sessionsResponse = await fetch(`${controlPlaneUrl}/api/sessions?limit=1`);
      if (sessionsResponse.ok) {
        const sessions = await sessionsResponse.json();
        if (sessions && sessions.length > 0) {
          const currentSession = sessions[0];
          
          const updateData = {
            end_time: endTime.toISOString(),
            status: "completed",
            metrics_data: recordedData.metrics || metrics,
            transactions_data: recordedData.transactions || transactions,
            duration_minutes: duration
          };

          const updateResponse = await fetch(`${controlPlaneUrl}/api/sessions/${currentSession.id}`, {
            method: 'PUT',
            headers: {
              'Content-Type': 'application/json',
            },
            body: JSON.stringify(updateData),
          });

          if (updateResponse.ok) {
            console.log('✅ Session updated successfully');
          } else {
            console.error('❌ Failed to update session:', await updateResponse.text());
          }
        }
      }
    } catch (error) {
      console.error('❌ Failed to update session:', error);
    }
    
    setRecordingStartTime(null);
  };

  const formatRecordingTime = () => {
    if (!recordingStartTime) return "00:00";
    const diff = Math.floor((currentTime.getTime() - recordingStartTime.getTime()) / 1000);
    const minutes = Math.floor(diff / 60);
    const seconds = diff % 60;
    return `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
  };

  return (
    <>
      <div className="glass-morphism rounded-2xl p-6 bg-gradient-to-br from-purple-500/10 to-pink-500/10 border border-purple-500/20 max-w-2xl mx-auto mb-8">
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
            <Link href="/sessions">
              <Button className="bg-white/10 text-white border border-white/20 hover:bg-white/20 hover:border-white/30 transition-all duration-200 backdrop-blur-sm">
                <Users className="w-4 h-4 mr-2" />
                세션 관리
              </Button>
            </Link>
            
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
              📹 모니터링 데이터를 실시간으로 레코딩하고 있습니다. 중지 후 세션 관리에서 데이터를 확인하세요.
            </p>
            <div className="mt-2 grid grid-cols-2 gap-4 text-xs">
              <div>
                <span className="text-white/40">메트릭 데이터: </span>
                <span className="text-white/80">{metrics.length}개</span>
              </div>
              <div>
                <span className="text-white/40">트랜잭션: </span>
                <span className="text-white/80">{transactions.length}개</span>
              </div>
            </div>
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