/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        discord: {
          dark: '#202225',
          light: '#2f3136',
          lighter: '#36393f',
          lightest: '#40444b',
          hover: '#32353b',
          text: '#dcddde',
          muted: '#72767d',
          green: '#43b581',
          blue: '#7289da',
          divider: '#202225',
        }
      }
    },
  },
  plugins: [],
}
