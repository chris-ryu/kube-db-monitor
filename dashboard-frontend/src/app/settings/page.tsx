'use client'

import React, { useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { 
  Settings as SettingsIcon, 
  Database, 
  Clock, 
  AlertTriangle,
  Save,
  RefreshCw,
  Server,
  HardDrive,
  Activity
} from "lucide-react";

export default function Settings() {
  const [settings, setSettings] = useState({
    longRunningThreshold: 5000,
    maxMetricsHistory: 1000,
    sessionRetentionDays: 30,
    autoDeleteSessions: true,
    alertsEnabled: true,
    deadlockAlert: true,
    connectionPoolAlert: true
  });
  
  const [systemInfo, setSystemInfo] = useState({
    version: '1.0.0',
    uptime: '2 days, 14 hours',
    memoryUsage: '256MB / 512MB',
    diskUsage: '2.1GB / 10GB',
    sessionsCount: 15,
    totalMetrics: 45892
  });

  const handleSettingChange = (key: string, value: any) => {
    setSettings(prev => ({
      ...prev,
      [key]: value
    }));
  };

  const saveSettings = async () => {
    try {
      // API 호출로 설정 저장
      console.log('Settings saved:', settings);
      // 임시로 성공 메시지 표시
      alert('설정이 저장되었습니다.');
    } catch (error) {
      console.error('설정 저장 실패:', error);
      alert('설정 저장에 실패했습니다.');
    }
  };

  const resetSettings = () => {
    setSettings({
      longRunningThreshold: 5000,
      maxMetricsHistory: 1000,
      sessionRetentionDays: 30,
      autoDeleteSessions: true,
      alertsEnabled: true,
      deadlockAlert: true,
      connectionPoolAlert: true
    });
  };

  return (
    <div className="min-h-screen p-6">
      <div className="max-w-6xl mx-auto">
        <div className="text-center mb-8">
          <h1 className="text-4xl font-bold text-white mb-2">
            시스템 설정
          </h1>
          <p className="text-white/60 text-lg">
            모니터링 시스템 설정 및 상태 관리
          </p>
        </div>

        <div className="grid lg:grid-cols-2 gap-8">
          {/* Settings Panel */}
          <div className="space-y-6">
            {/* Monitoring Settings */}
            <div className="glass-morphism rounded-2xl p-6 bg-gradient-to-br from-white/5 to-white/10 border border-white/20">
              <h2 className="text-xl font-bold text-white mb-4 flex items-center gap-2">
                <Database className="w-5 h-5 text-purple-400" />
                모니터링 설정
              </h2>
              
              <div className="space-y-4">
                <div>
                  <label className="text-white/80 text-sm font-medium mb-2 block">
                    Long-running 트랜잭션 임계값 (ms)
                  </label>
                  <Input
                    type="number"
                    value={settings.longRunningThreshold}
                    onChange={(e) => handleSettingChange('longRunningThreshold', parseInt(e.target.value))}
                    className="glass-morphism border-white/20 text-white"
                  />
                  <p className="text-white/40 text-xs mt-1">
                    이 시간을 초과하는 트랜잭션은 경고로 표시됩니다
                  </p>
                </div>

                <div>
                  <label className="text-white/80 text-sm font-medium mb-2 block">
                    최대 메트릭 기록 수
                  </label>
                  <Input
                    type="number"
                    value={settings.maxMetricsHistory}
                    onChange={(e) => handleSettingChange('maxMetricsHistory', parseInt(e.target.value))}
                    className="glass-morphism border-white/20 text-white"
                  />
                  <p className="text-white/40 text-xs mt-1">
                    메모리에 보관할 최대 메트릭 데이터 포인트 수
                  </p>
                </div>

                <div>
                  <label className="text-white/80 text-sm font-medium mb-2 block">
                    세션 보관 기간 (일)
                  </label>
                  <Input
                    type="number"
                    value={settings.sessionRetentionDays}
                    onChange={(e) => handleSettingChange('sessionRetentionDays', parseInt(e.target.value))}
                    className="glass-morphism border-white/20 text-white"
                  />
                  <p className="text-white/40 text-xs mt-1">
                    이 기간이 지난 세션은 자동으로 삭제됩니다
                  </p>
                </div>
              </div>
            </div>

            {/* Alert Settings */}
            <div className="glass-morphism rounded-2xl p-6 bg-gradient-to-br from-white/5 to-white/10 border border-white/20">
              <h2 className="text-xl font-bold text-white mb-4 flex items-center gap-2">
                <AlertTriangle className="w-5 h-5 text-orange-400" />
                알림 설정
              </h2>

              <div className="space-y-4">
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-white/80 font-medium">알림 활성화</p>
                    <p className="text-white/40 text-sm">모든 알림 기능을 활성화합니다</p>
                  </div>
                  <Button
                    onClick={() => handleSettingChange('alertsEnabled', !settings.alertsEnabled)}
                    className={`${settings.alertsEnabled ? 'bg-green-500/20 text-green-400 border-green-500/30' : 'bg-gray-500/20 text-gray-400 border-gray-500/30'} transition-all`}
                  >
                    {settings.alertsEnabled ? '켜짐' : '꺼짐'}
                  </Button>
                </div>

                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-white/80 font-medium">데드락 감지 알림</p>
                    <p className="text-white/40 text-sm">데드락 발생 시 즉시 알림</p>
                  </div>
                  <Button
                    onClick={() => handleSettingChange('deadlockAlert', !settings.deadlockAlert)}
                    disabled={!settings.alertsEnabled}
                    className={`${settings.deadlockAlert && settings.alertsEnabled ? 'bg-red-500/20 text-red-400 border-red-500/30' : 'bg-gray-500/20 text-gray-400 border-gray-500/30'} transition-all`}
                  >
                    {settings.deadlockAlert && settings.alertsEnabled ? '켜짐' : '꺼짐'}
                  </Button>
                </div>

                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-white/80 font-medium">Connection Pool 알림</p>
                    <p className="text-white/40 text-sm">연결 풀 임계값 초과 시 알림</p>
                  </div>
                  <Button
                    onClick={() => handleSettingChange('connectionPoolAlert', !settings.connectionPoolAlert)}
                    disabled={!settings.alertsEnabled}
                    className={`${settings.connectionPoolAlert && settings.alertsEnabled ? 'bg-blue-500/20 text-blue-400 border-blue-500/30' : 'bg-gray-500/20 text-gray-400 border-gray-500/30'} transition-all`}
                  >
                    {settings.connectionPoolAlert && settings.alertsEnabled ? '켜짐' : '꺼짐'}
                  </Button>
                </div>

                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-white/80 font-medium">세션 자동 삭제</p>
                    <p className="text-white/40 text-sm">보관 기간이 지난 세션을 자동으로 삭제</p>
                  </div>
                  <Button
                    onClick={() => handleSettingChange('autoDeleteSessions', !settings.autoDeleteSessions)}
                    className={`${settings.autoDeleteSessions ? 'bg-yellow-500/20 text-yellow-400 border-yellow-500/30' : 'bg-gray-500/20 text-gray-400 border-gray-500/30'} transition-all`}
                  >
                    {settings.autoDeleteSessions ? '켜짐' : '꺼짐'}
                  </Button>
                </div>
              </div>
            </div>

            {/* Action Buttons */}
            <div className="flex gap-3">
              <Button
                onClick={saveSettings}
                className="flex-1 bg-purple-500/20 text-purple-400 border border-purple-500/30 hover:bg-purple-500/30"
              >
                <Save className="w-4 h-4 mr-2" />
                설정 저장
              </Button>
              <Button
                onClick={resetSettings}
                className="bg-gray-500/20 text-gray-400 border border-gray-500/30 hover:bg-gray-500/30"
              >
                <RefreshCw className="w-4 h-4 mr-2" />
                초기화
              </Button>
            </div>
          </div>

          {/* System Info Panel */}
          <div className="space-y-6">
            {/* System Status */}
            <div className="glass-morphism rounded-2xl p-6 bg-gradient-to-br from-white/5 to-white/10 border border-white/20">
              <h2 className="text-xl font-bold text-white mb-4 flex items-center gap-2">
                <Server className="w-5 h-5 text-green-400" />
                시스템 상태
              </h2>

              <div className="space-y-4">
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-white/80 font-medium">시스템 버전</p>
                    <p className="text-white/40 text-sm">FlowLight DB Monitor</p>
                  </div>
                  <Badge className="bg-green-500/20 text-green-400 border-green-500/30">
                    v{systemInfo.version}
                  </Badge>
                </div>

                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-white/80 font-medium">가동 시간</p>
                    <p className="text-white/40 text-sm">시스템 연속 실행 시간</p>
                  </div>
                  <p className="text-white/80 font-mono text-sm">{systemInfo.uptime}</p>
                </div>

                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <Activity className="w-4 h-4 text-blue-400" />
                    <div>
                      <p className="text-white/80 font-medium">메모리 사용량</p>
                      <p className="text-white/40 text-sm">현재 / 할당된 메모리</p>
                    </div>
                  </div>
                  <p className="text-white/80 font-mono text-sm">{systemInfo.memoryUsage}</p>
                </div>

                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <HardDrive className="w-4 h-4 text-purple-400" />
                    <div>
                      <p className="text-white/80 font-medium">디스크 사용량</p>
                      <p className="text-white/40 text-sm">세션 저장소 사용량</p>
                    </div>
                  </div>
                  <p className="text-white/80 font-mono text-sm">{systemInfo.diskUsage}</p>
                </div>
              </div>
            </div>

            {/* Statistics */}
            <div className="glass-morphism rounded-2xl p-6 bg-gradient-to-br from-white/5 to-white/10 border border-white/20">
              <h2 className="text-xl font-bold text-white mb-4 flex items-center gap-2">
                <Clock className="w-5 h-5 text-cyan-400" />
                통계 정보
              </h2>

              <div className="grid grid-cols-2 gap-4">
                <div className="text-center p-4 glass-morphism rounded-lg bg-black/20 border border-white/10">
                  <p className="text-2xl font-bold text-white mb-1">{systemInfo.sessionsCount}</p>
                  <p className="text-white/60 text-sm">저장된 세션</p>
                </div>
                <div className="text-center p-4 glass-morphism rounded-lg bg-black/20 border border-white/10">
                  <p className="text-2xl font-bold text-white mb-1">{systemInfo.totalMetrics.toLocaleString()}</p>
                  <p className="text-white/60 text-sm">수집된 메트릭</p>
                </div>
              </div>

              <div className="mt-4 pt-4 border-t border-white/10">
                <div className="flex items-center justify-between text-sm">
                  <span className="text-white/60">Long-running 임계값</span>
                  <span className="text-white/80">{settings.longRunningThreshold}ms</span>
                </div>
                <div className="flex items-center justify-between text-sm mt-2">
                  <span className="text-white/60">최대 메트릭 기록</span>
                  <span className="text-white/80">{settings.maxMetricsHistory.toLocaleString()}개</span>
                </div>
                <div className="flex items-center justify-between text-sm mt-2">
                  <span className="text-white/60">세션 보관 기간</span>
                  <span className="text-white/80">{settings.sessionRetentionDays}일</span>
                </div>
              </div>
            </div>

            {/* Status Indicators */}
            <div className="glass-morphism rounded-2xl p-6 bg-gradient-to-br from-white/5 to-white/10 border border-white/20">
              <h2 className="text-xl font-bold text-white mb-4">서비스 상태</h2>
              
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <span className="text-white/80">Control Plane</span>
                  <div className="flex items-center gap-2">
                    <div className="w-2 h-2 rounded-full bg-green-400 animate-pulse"></div>
                    <span className="text-green-400 text-sm">정상</span>
                  </div>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-white/80">WebSocket 연결</span>
                  <div className="flex items-center gap-2">
                    <div className="w-2 h-2 rounded-full bg-green-400 animate-pulse"></div>
                    <span className="text-green-400 text-sm">연결됨</span>
                  </div>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-white/80">세션 저장소</span>
                  <div className="flex items-center gap-2">
                    <div className="w-2 h-2 rounded-full bg-green-400 animate-pulse"></div>
                    <span className="text-green-400 text-sm">사용 가능</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}