/** @type {import('tailwindcss').Config} */
module.exports = {
  darkMode: ["class"],
  content: [
    './src/pages/**/*.{js,ts,jsx,tsx,mdx}',
    './src/components/**/*.{js,ts,jsx,tsx,mdx}',
    './src/app/**/*.{js,ts,jsx,tsx,mdx}',
  ],
  theme: {
    extend: {
      borderRadius: {
        lg: 'var(--radius)',
        md: 'calc(var(--radius) - 2px)',
        sm: 'calc(var(--radius) - 4px)'
      },
      colors: {
        background: 'hsl(var(--background))',
        foreground: 'hsl(var(--foreground))',
        card: {
          DEFAULT: 'hsl(var(--card))',
          foreground: 'hsl(var(--card-foreground))'
        },
        popover: {
          DEFAULT: 'hsl(var(--popover))',
          foreground: 'hsl(var(--popover-foreground))'
        },
        primary: {
          DEFAULT: 'hsl(var(--primary))',
          foreground: 'hsl(var(--primary-foreground))'
        },
        secondary: {
          DEFAULT: 'hsl(var(--secondary))',
          foreground: 'hsl(var(--secondary-foreground))'
        },
        muted: {
          DEFAULT: 'hsl(var(--muted))',
          foreground: 'hsl(var(--muted-foreground))'
        },
        accent: {
          DEFAULT: 'hsl(var(--accent))',
          foreground: 'hsl(var(--accent-foreground))'
        },
        destructive: {
          DEFAULT: 'hsl(var(--destructive))',
          foreground: 'hsl(var(--destructive-foreground))'
        },
        border: 'hsl(var(--border))',
        input: 'hsl(var(--input))',
        ring: 'hsl(var(--ring))',
        chart: {
          '1': 'hsl(var(--chart-1))',
          '2': 'hsl(var(--chart-2))',
          '3': 'hsl(var(--chart-3))',
          '4': 'hsl(var(--chart-4))',
          '5': 'hsl(var(--chart-5))'
        },
        // Glassmorphism colors for dark theme
        glass: {
          light: 'rgba(255, 255, 255, 0.05)',
          medium: 'rgba(255, 255, 255, 0.1)',
          dark: 'rgba(15, 23, 42, 0.3)',
          darker: 'rgba(15, 23, 42, 0.6)',
        },
        gradient: {
          'dark-blue': '#1e1b4b',
          'mid-blue': '#312e81',
          'bright-blue': '#4338ca',
          'ocean-blue': '#1e40af',
        },
        success: '#00ff88',
        warning: '#ffab00',
        error: '#ff5252',
        info: '#2196f3',
      },
      keyframes: {
        'accordion-down': {
          from: {
            height: '0'
          },
          to: {
            height: 'var(--radix-accordion-content-height)'
          }
        },
        'accordion-up': {
          from: {
            height: 'var(--radix-accordion-content-height)'
          },
          to: {
            height: '0'
          }
        },
        'pulse-glow': {
          '0%, 100%': { opacity: '1', boxShadow: '0 0 5px rgba(0, 255, 136, 0.5)' },
          '50%': { opacity: '0.8', boxShadow: '0 0 20px rgba(0, 255, 136, 0.8)' }
        },
        'float': {
          '0%, 100%': { transform: 'translateY(0px)' },
          '50%': { transform: 'translateY(-10px)' }
        },
        'glass-float': {
          '0%, 100%': { transform: 'translateY(0px) rotateX(0deg) rotateY(0deg)' },
          '33%': { transform: 'translateY(-5px) rotateX(1deg) rotateY(1deg)' },
          '66%': { transform: 'translateY(5px) rotateX(-1deg) rotateY(-1deg)' }
        }
      },
      animation: {
        'accordion-down': 'accordion-down 0.2s ease-out',
        'accordion-up': 'accordion-up 0.2s ease-out',
        'pulse-glow': 'pulse-glow 2s infinite',
        'float': 'float 3s ease-in-out infinite',
        'shimmer': 'shimmer 1.5s infinite',
        'glass-float': 'glass-float 4s ease-in-out infinite',
        'gradient-shift': 'gradient-shift 6s ease-in-out infinite',
      },
      backdropBlur: {
        xs: '2px',
        '3xl': '64px',
      },
      fontFamily: {
        mono: ['JetBrains Mono', 'Fira Code', 'monospace'],
      },
      boxShadow: {
        'neon': '0 0 20px rgba(0, 255, 136, 0.5)',
        'neon-blue': '0 0 20px rgba(0, 153, 255, 0.5)',
        'neon-red': '0 0 20px rgba(255, 82, 82, 0.5)',
        'glass': '0 8px 32px rgba(31, 38, 135, 0.37)',
        'glass-lg': '0 25px 50px rgba(31, 38, 135, 0.5)',
        'glass-inset': 'inset 0 1px 0 rgba(255, 255, 255, 0.2)',
      },
      backgroundImage: {
        'gradient-dark-theme': 'linear-gradient(135deg, #1e1b4b 0%, #312e81 25%, #4338ca 50%, #1e40af 75%, #1e3a8a 100%)',
        'gradient-glass': 'linear-gradient(135deg, rgba(255,255,255,0.05) 0%, rgba(255,255,255,0.02) 100%)',
        'gradient-glass-dark': 'linear-gradient(135deg, rgba(15,23,42,0.4) 0%, rgba(15,23,42,0.2) 100%)',
      },
    },
  },
  plugins: [
    require("tailwindcss-animate"),
    function({ addUtilities }) {
      const newUtilities = {
        '.glass-morphism': {
          backdropFilter: 'blur(16px)',
          background: 'rgba(255, 255, 255, 0.1)',
          border: '1px solid rgba(255, 255, 255, 0.2)',
          boxShadow: '0 8px 32px rgba(0, 0, 0, 0.1)',
        },
        '.glass': {
          background: 'rgba(15, 23, 42, 0.4)',
          backdropFilter: 'blur(16px) saturate(1.2)',
          border: '1px solid rgba(255, 255, 255, 0.1)',
        },
        '.glass-dark': {
          background: 'rgba(15, 23, 42, 0.6)',
          backdropFilter: 'blur(20px) saturate(1.1)',
          border: '1px solid rgba(255, 255, 255, 0.08)',
        },
        '.glass-card': {
          background: 'rgba(15, 23, 42, 0.4)',
          backdropFilter: 'blur(20px) saturate(1.2)',
          border: '1px solid rgba(255, 255, 255, 0.1)',
          boxShadow: '0 8px 32px rgba(0, 0, 0, 0.3)',
        },
      }
      addUtilities(newUtilities, ['responsive', 'hover'])
    }
  ],
}