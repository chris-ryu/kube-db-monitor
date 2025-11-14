'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { Database, Video, Settings, Sparkles } from 'lucide-react'

const navigation = [
  {
    name: '대시보드',
    href: '/',
    icon: Database,
    description: '실시간 모니터링'
  },
  {
    name: '세션 관리',
    href: '/sessions',
    icon: Video,
    description: '저장된 세션'
  },
  {
    name: '설정',
    href: '/settings', 
    icon: Settings,
    description: '시스템 설정'
  }
]

export default function Navigation() {
  const pathname = usePathname()

  return (
    <nav className="fixed top-0 left-0 right-0 z-50 bg-black/20 backdrop-blur-md border-b border-white/10">
      <div className="max-w-7xl mx-auto px-6">
        <div className="flex items-center justify-between h-20">
          {/* Logo */}
          <div className="flex items-center gap-3">
            <div className="flex items-center justify-center w-10 h-10 rounded-xl bg-gradient-to-br from-purple-500/20 to-pink-500/20 border border-purple-500/30">
              <Sparkles className="w-6 h-6 text-purple-400" />
            </div>
            <div>
              <h1 className="text-xl font-bold text-white">FlowLight DB Monitor</h1>
              <p className="text-xs text-white/60">Database Performance Insights</p>
            </div>
          </div>

          {/* Navigation Links */}
          <div className="flex items-center gap-2">
            {navigation.map((item) => {
              const isActive = pathname === item.href
              const Icon = item.icon
              
              return (
                <Link
                  key={item.name}
                  href={item.href}
                  className={`
                    group flex items-center gap-3 px-4 py-2 rounded-xl transition-all duration-200
                    ${isActive 
                      ? 'bg-white/10 text-white border border-white/20 shadow-lg' 
                      : 'text-white/70 hover:text-white hover:bg-white/5'
                    }
                  `}
                >
                  <Icon className={`w-5 h-5 ${isActive ? 'text-purple-400' : 'text-white/60 group-hover:text-purple-400'}`} />
                  <div className="flex flex-col">
                    <span className="text-sm font-medium">{item.name}</span>
                    <span className="text-xs text-white/40 group-hover:text-white/60">{item.description}</span>
                  </div>
                </Link>
              )
            })}
          </div>

          {/* Status Indicator */}
          <div className="flex items-center gap-2">
            <div className="w-2 h-2 rounded-full bg-green-400 animate-pulse"></div>
            <span className="text-sm text-white/60">온라인</span>
          </div>
        </div>
      </div>
    </nav>
  )
}