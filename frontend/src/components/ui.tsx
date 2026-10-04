import type { ReactNode } from 'react';

export function Plate({ title, right, children, className = '' }: {
  title?: ReactNode;
  right?: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  return (
    <section className={`plate ${className}`}>
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

export function PageTitle({ children, right }: { children: ReactNode; right?: ReactNode }) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 mb-3">
      <h1 className="stencil text-[22px] leading-none">{children}</h1>
      {right}
    </div>
  );
}

export function Empty({ children }: { children: ReactNode }) {
  return <div className="px-3 py-6 text-muted text-[13px]">{children}</div>;
}

export function Loading() {
  return <div className="px-3 py-6 stencil text-muted text-[13px]">Loading…</div>;
}

/** A 409 from the server is a sentence written for a plant engineer; show it as-is. */
export function ErrorPlate({ error }: { error: unknown }) {
  if (!error) return null;
  const message = error instanceof Error ? error.message : String(error);
  return (
    <div role="alert" className="border border-warning text-warning bg-surface rounded px-3 py-2 text-[13px] mb-3">
      {message}
    </div>
  );
}

/** Engraved nameplate row: stencilled label over a monospace value. */
export function Engraved({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="px-3 py-2 border-b border-r border-engrave min-w-0">
      <div className="stencil text-[11px] text-muted">{label}</div>
      <div className="data text-label truncate">{children ?? '—'}</div>
    </div>
  );
}

export function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="block">
      <span className="stencil text-[11px] text-muted block mb-1">{label}</span>
      {children}
    </label>
  );
}

export function Scroll({ children }: { children: ReactNode }) {
  return <div className="overflow-x-auto">{children}</div>;
}
