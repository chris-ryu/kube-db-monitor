import React, { useState, useEffect } from "react";
import { User } from "@/api/entities";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { 
  Settings as SettingsIcon, 
  Shield, 
  Key, 
  User as UserIcon, 
  Save,
  CheckCircle,
  AlertTriangle
} from "lucide-react";

import PasswordChangeForm from "../components/settings/PasswordChangeForm";
import AccountInfo from "../components/settings/AccountInfo";

export default function Settings() {
  const [currentUser, setCurrentUser] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [activeTab, setActiveTab] = useState("account");

  useEffect(() => {
    loadCurrentUser();
  }, []);

  const loadCurrentUser = async () => {
    try {
      const user = await User.me();
      setCurrentUser(user);
    } catch (error) {
      console.error("사용자 정보 로드 실패:", error);
    }
    setIsLoading(false);
  };

  const isAdmin = currentUser?.role === "admin";

  const tabs = [
    {
      id: "account",
      label: "계정 정보",
      icon: UserIcon,
      description: "기본 계정 정보 및 프로필"
    },
    {
      id: "security", 
      label: "보안 설정",
      icon: Shield,
      description: "비밀번호 변경 및 보안 옵션"
    }
  ];

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="glass-morphism rounded-2xl p-8">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-white/60 mx-auto"></div>
          <p className="text-white/60 mt-4 text-center">설정 로딩 중...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen p-6">
      <div className="max-w-6xl mx-auto">
        {/* Header */}
        <div className="text-center mb-8">
          <h1 className="text-4xl font-bold text-white mb-2 flex items-center justify-center gap-3">
            <SettingsIcon className="w-10 h-10" />
            시스템 설정
          </h1>
          <p className="text-white/60 text-lg">
            계정 관리 및 시스템 설정을 관리하세요
          </p>
          {isAdmin && (
            <Badge className="bg-yellow-500/20 text-yellow-400 border-yellow-500/30 mt-2">
              <Shield className="w-3 h-3 mr-1" />
              관리자 계정
            </Badge>
          )}
        </div>

        <div className="grid lg:grid-cols-4 gap-6">
          {/* Navigation Sidebar */}
          <div className="lg:col-span-1">
            <div className="glass-morphism rounded-2xl p-6 bg-gradient-to-br from-white/5 to-white/10 border border-white/20 sticky top-6">
              <nav className="space-y-2">
                {tabs.map((tab) => (
                  <button
                    key={tab.id}
                    onClick={() => setActiveTab(tab.id)}
                    className={`w-full flex items-start gap-3 p-3 rounded-lg transition-all duration-200 text-left ${
                      activeTab === tab.id
                        ? 'bg-white/20 text-white border border-white/30'
                        : 'text-white/70 hover:text-white hover:bg-white/10'
                    }`}
                  >
                    <tab.icon className="w-5 h-5 flex-shrink-0 mt-0.5" />
                    <div>
                      <p className="font-medium">{tab.label}</p>
                      <p className="text-xs opacity-80 mt-1">{tab.description}</p>
                    </div>
                  </button>
                ))}
              </nav>
            </div>
          </div>

          {/* Main Content */}
          <div className="lg:col-span-3">
            <div className="space-y-6">
              {activeTab === "account" && (
                <AccountInfo user={currentUser} onUserUpdate={setCurrentUser} />
              )}
              
              {activeTab === "security" && (
                <PasswordChangeForm user={currentUser} />
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}