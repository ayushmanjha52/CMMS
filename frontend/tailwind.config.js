/** @type {import('tailwindcss').Config} */
// Colours are ANSI Z535 safety colours and each has exactly one job. They are exposed as
// semantic names only — there is deliberately no "primary" or "accent" to reach for.
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        surface: 'var(--surface)',
        raised: 'var(--surface-raised)',
        engrave: 'var(--engrave)',
        label: 'var(--label)',
        muted: 'var(--label-muted)',
        danger: 'var(--danger)',
        warning: 'var(--warning)',
        caution: 'var(--caution)',
        safe: 'var(--safe)',
        info: 'var(--info)',
      },
      fontFamily: {
        stencil: ['"Barlow Condensed"', 'Arial Narrow', 'sans-serif'],
        body: ['"Source Sans 3"', 'system-ui', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'ui-monospace', 'monospace'],
      },
      borderRadius: {
        DEFAULT: '2px',
        sm: '2px',
      },
    },
  },
  plugins: [],
};
