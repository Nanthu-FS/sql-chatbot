/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{js,ts,jsx,tsx}"],
  theme: {
    extend: {
      colors: {
        navy: {
          900: "#012443",
          800: "#01304f",
          700: "#023d63",
          600: "#035380",
        },
        accent: {
          red: "#FF6B6B",
          green: "#4ADE80",
          yellow: "#FBBF24",
          blue: "#60A5FA",
        },
      },
    },
  },
  plugins: [],
};
