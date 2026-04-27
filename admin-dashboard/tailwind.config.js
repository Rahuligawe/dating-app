/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        sidebar: '#1E1E35',
        'sidebar-hover': '#2A2A4A',
        'aura-pink': '#E91E8C',
        'aura-purple': '#7B2FBE',
        'card-bg': '#F8F9FC',
      }
    }
  },
  plugins: []
}
