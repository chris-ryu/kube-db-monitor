
import React from "react";
import { Link, useLocation } from "react-router-dom";
import { createPageUrl } from "@/utils";
import { Activity, Database, BarChart3, Settings, Video } from "lucide-react";

export default function Layout({ children, currentPageName }) {
  const location = useLocation();

  return (
    <div className="min-h-screen relative overflow-hidden">
      {/* Animated Background Gradients */}
      <div className="fixed inset-0 bg-slate-900">
        <div className="absolute inset-0 bg-gradient-to-br from-purple-600/20 via-pink-500/10 to-cyan-500/20"></div>
        <div className="absolute top-0 left-1/4 w-96 h-96 bg-gradient-to-r from-violet-500/30 to-purple-500/30 rounded-full blur-3xl animate-pulse"></div>
        <div className="absolute bottom-0 right-1/4 w-80 h-80 bg-gradient-to-r from-cyan-500/30 to-blue-500/30 rounded-full blur-3xl animate-pulse delay-1000"></div>
        <div className="absolute top-1/2 left-1/2 w-64 h-64 bg-gradient-to-r from-pink-500/20 to-rose-500/20 rounded-full blur-3xl animate-pulse delay-500"></div>
      </div>

      {/* Glass Navigation Header */}
      <header className="relative z-10 border-b border-white/10">
        <div className="backdrop-blur-xl bg-white/5 shadow-xl">
          <div className="max-w-7xl mx-auto px-6 py-4">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-3">
                <div className="p-2 rounded-xl bg-gradient-to-br from-purple-500/20 to-cyan-500/20 backdrop-blur-sm border border-white/20">
                  <Database className="w-6 h-6 text-white" />
                </div>
                <div>
                  <h1 className="text-xl font-bold text-white">FlowLight</h1>
                  <p className="text-sm text-white/60">실시간 성능 분석 & AI 인사이트</p>
                </div>
              </div>
              
              <nav className="hidden md:flex items-center gap-1">
                <Link
                  to={createPageUrl("Dashboard")}
                  className={`flex items-center gap-2 px-4 py-2 rounded-lg transition-all duration-200 backdrop-blur-sm ${
                  location.pathname === createPageUrl("Dashboard") ?
                  'text-white bg-white/10' :
                  'text-white/80 hover:text-white hover:bg-white/10'}`
                  }>

                  <BarChart3 className="w-4 h-4" />
                  대시보드
                </Link>
                <Link
                  to={createPageUrl("Sessions")}
                  className={`flex items-center gap-2 px-4 py-2 rounded-lg transition-all duration-200 backdrop-blur-sm ${
                  location.pathname === createPageUrl("Sessions") ?
                  'text-white bg-white/10' :
                  'text-white/80 hover:text-white hover:bg-white/10'}`
                  }>

                  <Video className="w-4 h-4" />
                  레코딩 세션
                </Link>
                <Link
                  to={createPageUrl("Settings")}
                  className={`flex items-center gap-2 px-4 py-2 rounded-lg transition-all duration-200 backdrop-blur-sm ${
                  location.pathname === createPageUrl("Settings") ?
                  'text-white bg-white/10' :
                  'text-white/80 hover:text-white hover:bg-white/10'}`
                  }>

                  <Settings className="w-4 h-4" />
                  설정
                </Link>
              </nav>
            </div>
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main className="relative z-10 flex-1">
        {children}
      </main>

      <style jsx>{`
        @keyframes float {
          0%, 100% { transform: translateY(0px) rotate(0deg); }
          50% { transform: translateY(-20px) rotate(5deg); }
        }
        
        .animate-float {
          animation: float 6s ease-in-out infinite;
        }
        
        .glass-morphism {
          backdrop-filter: blur(16px);
          background: rgba(255, 255, 255, 0.1);
          border: 1px solid rgba(255, 255, 255, 0.2);
          box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
        }
      `}</style>
    </div>);

}
