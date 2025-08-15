import { Inter } from 'next/font/google'
import './globals.css'

const inter = Inter({ subsets: ['latin'] })

export const metadata = {
  title: 'KubeDB Monitor Dashboard',
  description: 'Real-time database performance monitoring dashboard',
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
    title: process.env.NEXT_PUBLIC_DASHBOARD_TITLE || '🚀 Advanced KubeDB Monitor Dashboard',
    longRunningThresholdMs: parseInt(process.env.NEXT_PUBLIC_LONG_RUNNING_THRESHOLD_MS || '4000')
  }

  // 서버사이드 로깅 (Docker logs에서 확인 가능)
  console.log('🔧 Server-side runtime config:', {
    title: dashboardConfig.title,
    rawEnvValue: process.env.NEXT_PUBLIC_DASHBOARD_TITLE,
    allEnvKeys: Object.keys(process.env).filter(key => key.includes('DASHBOARD'))
  })

  return (
    <html lang="en">
      <body className={inter.className}>
        <script
          dangerouslySetInnerHTML={{
            __html: `
              window.__RUNTIME_CONFIG__ = ${JSON.stringify(dashboardConfig)};
              console.log('📦 Runtime config injected:', window.__RUNTIME_CONFIG__);
            `,
          }}
        />
        {children}
      </body>
    </html>
  )
}