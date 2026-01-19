/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        primary: '#006d3e',
        secondary: '#4c6356',
        tertiary: '#f97316',
      }
    },
  },
  plugins: [],
}