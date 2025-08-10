import './globals.css'

export const metadata = {
  title: 'KubeDB Monitor Dashboard',
  description: 'Real-time database monitoring dashboard',
}

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  )
}