// Small line icons, drawn inline so there is no icon-font dependency.
import { useId } from 'react';

type P = { className?: string };
const base = { fill: 'none', stroke: 'currentColor', strokeWidth: 1.8, strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const };

export const IconBoard = ({ className = 'w-4 h-4' }: P) => (
  <svg viewBox="0 0 24 24" className={className} {...base}><rect x="3" y="3" width="7" height="9" rx="1.5" /><rect x="14" y="3" width="7" height="5" rx="1.5" /><rect x="14" y="12" width="7" height="9" rx="1.5" /><rect x="3" y="16" width="7" height="5" rx="1.5" /></svg>
);
export const IconWrench = ({ className = 'w-4 h-4' }: P) => (
  <svg viewBox="0 0 24 24" className={className} {...base}><path d="M14.7 6.3a4 4 0 0 0-5.4 5.2L3 17.8V21h3.2l6.3-6.3a4 4 0 0 0 5.2-5.4l-2.6 2.6-2.4-.6-.6-2.4z" /></svg>
);
export const IconTree = ({ className = 'w-4 h-4' }: P) => (
  <svg viewBox="0 0 24 24" className={className} {...base}><rect x="9" y="2" width="6" height="5" rx="1" /><rect x="2" y="17" width="6" height="5" rx="1" /><rect x="16" y="17" width="6" height="5" rx="1" /><path d="M12 7v5M5 17v-3h14v3" /></svg>
);
export const IconCalendar = ({ className = 'w-4 h-4' }: P) => (
  <svg viewBox="0 0 24 24" className={className} {...base}><rect x="3" y="4.5" width="18" height="16.5" rx="2" /><path d="M3 9.5h18M8 2.5v4M16 2.5v4M8 14l2.5 2.5L16 12" /></svg>
);
export const IconBox = ({ className = 'w-4 h-4' }: P) => (
  <svg viewBox="0 0 24 24" className={className} {...base}><path d="M21 8 12 3 3 8v8l9 5 9-5z" /><path d="m3 8 9 5 9-5M12 13v8" /></svg>
);
export const IconAlert = ({ className = 'w-4 h-4' }: P) => (
  <svg viewBox="0 0 24 24" className={className} {...base}><path d="M10.3 3.9 2 18a2 2 0 0 0 1.7 3h16.6a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z" /><path d="M12 9v4M12 17h.01" /></svg>
);
export const IconClock = ({ className = 'w-4 h-4' }: P) => (
  <svg viewBox="0 0 24 24" className={className} {...base}><circle cx="12" cy="12" r="9" /><path d="M12 7v5l3 2" /></svg>
);
export const IconBolt = ({ className = 'w-4 h-4' }: P) => (
  <svg viewBox="0 0 24 24" className={className} {...base}><path d="M13 2 4 14h7l-1 8 9-12h-7z" /></svg>
);
export const IconFlame = ({ className = 'w-4 h-4' }: P) => (
  <svg viewBox="0 0 24 24" className={className} {...base}><path d="M12 22c4 0 7-2.7 7-6.8 0-3.3-2.2-5.6-3.6-7.2-.3 1.9-1.3 3-2.4 3.4.4-3.3-1.2-6.4-4-8.4.3 3.1-1.6 5.3-3 7A7 7 0 0 0 5 15.2C5 19.3 8 22 12 22z" /></svg>
);
export const IconTrendDown = ({ className = 'w-4 h-4' }: P) => (
  <svg viewBox="0 0 24 24" className={className} {...base}><path d="m3 7 7 7 4-4 7 7" /><path d="M21 11v6h-6" /></svg>
);

/** The PlantDesk mark: a thermal-camera reticle over a heat bloom. */
export function Logo({ className = 'w-8 h-8' }: P) {
  // Unique per instance: with a shared id, every logo resolves to the first one in the DOM,
  // and if that one sits in a hidden container (the mobile bar on desktop) none render.
  const gradientId = `pd-bloom-${useId().replace(/:/g, '')}`;
  return (
    <svg viewBox="0 0 40 40" className={className} aria-hidden="true">
      <defs>
        <radialGradient id={gradientId} cx="50%" cy="55%" r="55%">
          <stop offset="0%" stopColor="#ffd23f" />
          <stop offset="35%" stopColor="#ff8a3d" />
          <stop offset="65%" stopColor="#ff3d7f" />
          <stop offset="100%" stopColor="#7b2ff7" />
        </radialGradient>
      </defs>
      <rect x="1" y="1" width="38" height="38" rx="10" fill={`url(#${gradientId})`} />
      <g stroke="#08080b" strokeWidth="2.6" strokeLinecap="round" fill="none">
        <path d="M9 15V9h6M25 9h6v6M31 25v6h-6M15 31H9v-6" />
        <circle cx="20" cy="20" r="4.2" />
      </g>
    </svg>
  );
}
