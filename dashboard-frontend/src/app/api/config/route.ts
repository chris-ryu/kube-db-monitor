import { NextResponse } from 'next/server'

export async function GET() {
  return NextResponse.json({
    title: process.env.NEXT_PUBLIC_DASHBOARD_TITLE || '🚀 Advanced KubeDB Monitor Dashboard',
    longRunningThresholdMs: parseInt(process.env.NEXT_PUBLIC_LONG_RUNNING_THRESHOLD_MS || '4000')
  })
}