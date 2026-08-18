/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    './src/**/*.{html,ts}',
  ],
  theme: {
    extend: {
      colors: {
        dark: {
          DEFAULT: 'var(--c-dark)',
          card: 'var(--c-dark-card)',
          surface: 'var(--c-dark-surface)',
          border: 'var(--c-dark-border)',
          hover: 'var(--c-dark-hover)',
        },
        neon: {
          purple: '#7c3aed',
          'purple-glow': '#a855f7',
          'purple-light': '#c084fc',
          cyan: '#06b6d4',
          'cyan-glow': '#22d3ee',
          pink: '#f0abfc',
          green: '#4ade80',
          red: '#f87171',
          orange: '#fb923c',
        },
        muted: '#6b7280',
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
        mono: ['JetBrains Mono', 'monospace'],
      },
      boxShadow: {
        neon: '0 0 20px rgba(124, 58, 237, 0.4), 0 0 40px rgba(124, 58, 237, 0.15)',
        'neon-cyan': '0 0 20px rgba(6, 182, 212, 0.4), 0 0 40px rgba(6, 182, 212, 0.15)',
        'neon-pink': '0 0 20px rgba(240, 171, 252, 0.4)',
        card: '0 4px 24px rgba(0,0,0,0.4)',
        'card-hover': '0 8px 40px rgba(0,0,0,0.6)',
      },
      backgroundImage: {
        'gradient-neon': 'linear-gradient(135deg, #7c3aed, #06b6d4)',
        'gradient-neon-rev': 'linear-gradient(135deg, #06b6d4, #7c3aed)',
        'gradient-dark': 'linear-gradient(180deg, var(--c-dark) 0%, var(--c-dark-card) 100%)',
        'gradient-card': 'linear-gradient(135deg, rgba(124,58,237,0.1), rgba(6,182,212,0.05))',
      },
      animation: {
        'pulse-neon': 'pulseNeon 2s ease-in-out infinite',
        'fade-in': 'fadeIn 0.4s ease-out',
        'slide-in': 'slideIn 0.3s ease-out',
        'float': 'float 6s ease-in-out infinite',
      },
      keyframes: {
        pulseNeon: {
          '0%, 100%': { boxShadow: '0 0 20px rgba(124, 58, 237, 0.4)' },
          '50%': { boxShadow: '0 0 40px rgba(124, 58, 237, 0.8), 0 0 80px rgba(124, 58, 237, 0.3)' },
        },
        fadeIn: {
          '0%': { opacity: '0', transform: 'translateY(8px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        slideIn: {
          '0%': { opacity: '0', transform: 'translateX(-12px)' },
          '100%': { opacity: '1', transform: 'translateX(0)' },
        },
        float: {
          '0%, 100%': { transform: 'translateY(0px)' },
          '50%': { transform: 'translateY(-10px)' },
        },
      },
      borderRadius: {
        xl2: '1rem',
        xl3: '1.5rem',
      },
    },
  },
  plugins: [
    require('@tailwindcss/forms'),
  ],
};
