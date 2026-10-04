/** @type {import('tailwindcss').Config} */
// Status colours (danger … info) each have exactly one job. Brand colour comes from the
// infrared "heat" scale, which is never used to signal status.
const c = (name) => `rgb(var(--c-${name}) / <alpha-value>)`;

export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      // RGB-channel form so opacity modifiers work: bg-info/10, border-danger/50 …
      colors: {
        surface: c('surface'),
        raised: c('raised'),
        sunk: c('sunk'),
        engrave: c('engrave'),
        label: c('label'),
        muted: c('muted'),
        tag: c('tag'),
        danger: c('danger'),
        warning: c('warning'),
        caution: c('caution'),
        safe: c('safe'),
        info: c('info'),
        heat: {
          0: c('heat-0'),
          1: c('heat-1'),
          2: c('heat-2'),
          3: c('heat-3'),
          4: c('heat-4'),
          5: c('heat-5'),
        },
      },
      fontFamily: {
        stencil: ['"Barlow Condensed"', 'Arial Narrow', 'sans-serif'],
        body: ['"Source Sans 3"', 'system-ui', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'ui-monospace', 'monospace'],
      },
    },
  },
  plugins: [],
};
