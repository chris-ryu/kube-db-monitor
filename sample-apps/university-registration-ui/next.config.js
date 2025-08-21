/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'standalone',
  poweredByHeader: false,
  transpilePackages: [],
  experimental: {
    serverComponentsExternalPackages: [],
  },
}

module.exports = nextConfig