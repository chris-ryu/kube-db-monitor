import { Inter } from 'next/font/google'
import './globals.css'
import Navigation from '../components/Navigation'

const inter = Inter({ subsets: ['latin'] })

export const metadata = {
  title: 'FlowLight DB Monitor',
  description: '실시간 데이터베이스 성능 모니터링 대시보드',
}

// 동적 렌더링 강제 (환경변수를 런타임에 읽기 위해)
export const dynamic = 'force-dynamic'

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  // 서버사이드에서 실제 런타임 환경변수 읽기
  const dashboardConfig = {
    title: process.env.NEXT_PUBLIC_DASHBOARD_TITLE || 'FlowLight DB Monitor',
    longRunningThresholdMs: parseInt(process.env.NEXT_PUBLIC_LONG_RUNNING_THRESHOLD_MS || '4000')
  }

  // 서버사이드 로깅 (Docker logs에서 확인 가능)
  console.log('🔧 Server-side runtime config:', {
    title: dashboardConfig.title,
    rawEnvValue: process.env.NEXT_PUBLIC_DASHBOARD_TITLE,
    allEnvKeys: Object.keys(process.env).filter(key => key.includes('DASHBOARD'))
  })

  return (
    <html lang="ko">
      <body className={`${inter.className} min-h-screen bg-slate-900`}>
        <script
          dangerouslySetInnerHTML={{
            __html: `
              window.__RUNTIME_CONFIG__ = ${JSON.stringify(dashboardConfig)};
              console.log('📦 Runtime config injected:', window.__RUNTIME_CONFIG__);
            `,
          }}
        />
        
        {/* Animated Background Gradients */}
        <div className="fixed inset-0 bg-slate-900">
          <div className="absolute inset-0 bg-gradient-to-br from-purple-600/20 via-pink-500/10 to-cyan-500/20"></div>
          <div className="absolute top-0 left-1/4 w-96 h-96 bg-gradient-to-r from-violet-500/30 to-purple-500/30 rounded-full blur-3xl animate-pulse"></div>
          <div className="absolute bottom-0 right-1/4 w-80 h-80 bg-gradient-to-r from-cyan-500/30 to-blue-500/30 rounded-full blur-3xl animate-pulse delay-1000"></div>
          <div className="absolute top-1/2 left-1/2 w-64 h-64 bg-gradient-to-r from-pink-500/20 to-rose-500/20 rounded-full blur-3xl animate-pulse delay-500"></div>
        </div>
        
        <div className="relative z-10">
          <Navigation />
          <main className="pt-20">
            {children}
          </main>
        </div>
      </body>
    </html>
  )
}