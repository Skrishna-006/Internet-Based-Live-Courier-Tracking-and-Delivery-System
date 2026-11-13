/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      fontFamily: {
        sans: ['Inter', 'ui-sans-serif', 'system-ui', '-apple-system', 'BlinkMacSystemFont', 'Segoe UI', 'Roboto', 'sans-serif'],
      },
      colors: {
        // Core Premium Dark Palette
        navy: {
          950: '#080D19', // Main background
          900: '#0D1424', // Secondary background
          850: '#121C2E', // Card background
          800: '#18243A', // Elevated card
          750: '#1e2c45', // Highlight / hover card
          700: '#263449', // Subtle borders
          600: '#334460', // Active borders
        },
        // Accents
        accent: {
          blue: '#3B82F6',
          cyan: '#06B6D4',
          indigo: '#6366F1',
          purple: '#8B5CF6',
        },
        // Text tokens
        surface: {
          primary: '#F8FAFC',
          secondary: '#94A3B8',
          muted: '#64748B',
        },
        // Semantic status tokens
        status: {
          success: '#22C55E',
          warning: '#F59E0B',
          danger: '#EF4444',
          info: '#3B82F6',
        },
        // Preserved brand ramp for backward compatibility
        brand: {
          50: '#eff6ff',
          100: '#dbeafe',
          500: '#3b82f6',
          600: '#2563eb',
          700: '#1d4ed8',
          800: '#1e40af',
          900: '#1e3a8a',
        },
      },
      boxShadow: {
        'dark-sm': '0 1px 2px 0 rgba(0, 0, 0, 0.4)',
        'dark-card': '0 4px 20px -2px rgba(0, 0, 0, 0.5), 0 2px 6px -1px rgba(0, 0, 0, 0.3)',
        'dark-elevated': '0 10px 25px -3px rgba(0, 0, 0, 0.6), 0 4px 10px -2px rgba(0, 0, 0, 0.4)',
        'glow-blue': '0 0 15px -3px rgba(59, 130, 246, 0.25)',
        'glow-cyan': '0 0 15px -3px rgba(6, 182, 212, 0.25)',
      },
    },
  },
  plugins: [],
};
