import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';

export function Plate({ title, right, children, className = '' }: {
  title?: ReactNode;
  right?: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  return (
    <section className={`plate overflow-hidden ${className}`}>
      {title && (
        <div className="plate-head">
          <span>{title}</span>
          {right}
        </div>
      )}
      {children}
    </section>
  );
}

export function PageTitle({ children, right, kicker }: { children: ReactNode; right?: ReactNode; kicker?: ReactNode }) {
  return (
    <div className="flex flex-wrap items-end justify-between gap-4 mb-6">
      <div>
        {kicker && <div className="stencil text-[11px] text-heat-4 mb-2">{kicker}</div>}
        <h1 className="font-display text-[40px] sm:text-[46px] leading-[0.95] text-white">{children}</h1>
      </div>
      {right}
    </div>
  );
}

export function Empty({ children }: { children: ReactNode }) {
  return <div className="px-5 py-8 text-muted text-[13.5px]">{children}</div>;
}

export function Loading() {
  return (
    <div className="px-5 py-8 flex items-center gap-3 text-muted text-[13.5px]">
      <span className="w-2 h-2 rounded-full bg-heat-3 pulse-dot" />
      Loading…
    </div>
  );
}

/** A 409 from the server is a sentence written for a plant engineer; show it as-is. */
export function ErrorPlate({ error }: { error: unknown }) {
  if (!error) return null;
  const message = error instanceof Error ? error.message : String(error);
  return (
    <div role="alert" className="border border-warning/60 text-warning bg-warning/10 rounded-lg px-3 py-2 text-[13.5px] mb-3">
      {message}
    </div>
  );
}

/** Nameplate cell: small caption over a monospace value. */
export function Engraved({ label, children, tone }: { label: string; children: ReactNode; tone?: string }) {
  return (
    <div className="px-5 py-3.5 border-b border-r border-engrave/80 min-w-0">
      <div className="stencil text-[10.5px] text-muted">{label}</div>
      <div className={`data truncate mt-1 ${tone ?? 'text-label'}`}>{children ?? '—'}</div>
    </div>
  );
}

export function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="block">
      <span className="text-[12.5px] font-medium text-muted block mb-1.5">{label}</span>
      {children}
    </label>
  );
}

export function Scroll({ children }: { children: ReactNode }) {
  return <div className="overflow-x-auto">{children}</div>;
}

export const REPO_URL = 'https://github.com/ayushmanjha52/CMMS';

export function FooterLinks({ className = '' }: { className?: string }) {
  return (
    <div className={`text-[12px] text-muted flex flex-wrap items-center gap-x-4 gap-y-1 ${className}`}>
      <span>© {new Date().getFullYear()} PlantDesk</span>
      <Link to="/privacy" className="hover:text-label underline-offset-4 hover:underline">Privacy &amp; cookies</Link>
      <Link to="/terms" className="hover:text-label underline-offset-4 hover:underline">Terms</Link>
      <a href={REPO_URL} target="_blank" rel="noopener noreferrer" className="hover:text-label underline-offset-4 hover:underline">
        Source on GitHub
      </a>
    </div>
  );
}
