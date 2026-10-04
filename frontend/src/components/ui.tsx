import type { ReactNode } from 'react';

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
    <div className="flex flex-wrap items-end justify-between gap-3 mb-5">
      <div>
        {kicker && <div className="stencil text-[12px] text-heat-4 mb-1">{kicker}</div>}
        <h1 className="stencil text-[28px] leading-none text-white">{children}</h1>
        <div className="mt-2 h-[3px] w-16 rounded-full heat-bar" />
      </div>
      {right}
    </div>
  );
}

export function Empty({ children }: { children: ReactNode }) {
  return <div className="px-4 py-8 text-muted text-[13px]">{children}</div>;
}

export function Loading() {
  return (
    <div className="px-4 py-8 flex items-center gap-3 stencil text-muted text-[13px]">
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
    <div role="alert" className="border border-warning/60 text-warning bg-warning/10 rounded-lg px-3 py-2 text-[13px] mb-3">
      {message}
    </div>
  );
}

/** Engraved nameplate cell: stencilled label over a monospace value. */
export function Engraved({ label, children, tone }: { label: string; children: ReactNode; tone?: string }) {
  return (
    <div className="px-4 py-3 border-b border-r border-engrave/70 min-w-0">
      <div className="stencil text-[11px] text-muted">{label}</div>
      <div className={`data truncate mt-0.5 ${tone ?? 'text-label'}`}>{children ?? '—'}</div>
    </div>
  );
}

export function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="block">
      <span className="stencil text-[11px] text-muted block mb-1.5">{label}</span>
      {children}
    </label>
  );
}

export function Scroll({ children }: { children: ReactNode }) {
  return <div className="overflow-x-auto">{children}</div>;
}
