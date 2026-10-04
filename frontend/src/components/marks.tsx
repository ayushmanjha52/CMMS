import { Link } from 'react-router-dom';
import type { Criticality, PmState, Priority, WorkOrderStatus, WorkOrderType } from '../api/types';

/**
 * The asset tag: one fixed-format identifier threading through the whole app, always the
 * same warm peach, always clickable, always to the same place.
 */
export function AssetTag({ id, tag, className = '' }: { id: string; tag: string; className?: string }) {
  return (
    <Link
      to={`/assets/${id}`}
      className={`data text-tag underline decoration-dotted decoration-heat-3/60 underline-offset-4 hover:decoration-solid hover:text-white transition-colors ${className}`}
      title={`Asset ${tag}`}
    >
      {tag}
    </Link>
  );
}

// Colour carries meaning before text does, as on a safety sign.
const PRIORITY_STYLE: Record<Priority, string> = {
  EMERGENCY: 'text-white border-transparent bg-gradient-to-r from-[#ff2e63] to-danger shadow-[0_0_16px_-2px_rgba(255,77,109,0.85)]',
  HIGH: 'text-[#2a1600] border-transparent bg-warning shadow-[0_0_14px_-4px_rgba(255,166,43,0.9)]',
  MEDIUM: 'text-info border-info/50 bg-info/10',
  LOW: 'text-muted border-engrave bg-white/[0.03]',
};

export function PriorityMark({ priority }: { priority: Priority }) {
  return <span className={`chip ${PRIORITY_STYLE[priority]}`}>{priority === 'EMERGENCY' ? 'EMERG' : priority}</span>;
}

export function TypeMark({ type }: { type: WorkOrderType }) {
  return type === 'BREAKDOWN' ? (
    <span className="chip text-danger border-danger/40 bg-danger/10 h-5 px-1.5" title="Breakdown">
      BD
    </span>
  ) : (
    <span className="chip text-info border-info/40 bg-info/10 h-5 px-1.5" title="Preventive">
      PM
    </span>
  );
}

const STATUS: Record<WorkOrderStatus, { label: string; color: string; dot: string; pulse?: boolean }> = {
  OPEN: { label: 'Open', color: 'text-label', dot: 'bg-label' },
  IN_PROGRESS: { label: 'In progress', color: 'text-info', dot: 'bg-info', pulse: true },
  ON_HOLD: { label: 'On hold', color: 'text-muted', dot: 'bg-muted' },
  COMPLETED: { label: 'Returned to service', color: 'text-safe', dot: 'bg-safe' },
  CLOSED: { label: 'Closed', color: 'text-safe/70', dot: 'bg-safe/60' },
  CANCELLED: { label: 'Cancelled', color: 'text-muted line-through', dot: 'bg-muted/50' },
};

export function StatusLabel({ status, overdue }: { status: WorkOrderStatus; overdue?: boolean }) {
  const s = STATUS[status];
  const late = overdue && (status === 'OPEN' || status === 'IN_PROGRESS' || status === 'ON_HOLD');
  return (
    <span className={`stencil text-[12px] inline-flex items-center gap-2 ${late ? 'text-warning' : s.color}`}>
      <span className={`w-2 h-2 rounded-full ${late ? 'bg-warning' : s.dot} ${s.pulse ? 'pulse-dot' : ''}`} />
      {s.label}
      {late && <span className="chip h-5 px-1.5 text-[#2a1600] bg-warning border-transparent">Overdue</span>}
    </span>
  );
}

const PM_STYLE: Record<PmState, { label: string; cls: string }> = {
  OVERDUE: { label: 'Overdue', cls: 'text-[#2a1600] bg-warning border-transparent shadow-[0_0_14px_-4px_rgba(255,166,43,0.9)]' },
  DUE: { label: 'Due', cls: 'text-warning border-warning/60 bg-warning/10' },
  APPROACHING: { label: 'Approaching', cls: 'text-caution border-caution/50 bg-caution/10' },
  SCHEDULED: { label: 'Scheduled', cls: 'text-info border-info/50 bg-info/10' },
  OK: { label: 'Healthy', cls: 'text-safe border-safe/40 bg-safe/10' },
  INACTIVE: { label: 'Inactive', cls: 'text-muted border-engrave' },
};

export function PmStateMark({ state }: { state: PmState }) {
  const s = PM_STYLE[state];
  return <span className={`chip ${s.cls}`}>{s.label}</span>;
}

const CRIT_STYLE: Record<Criticality, string> = {
  A: 'text-white bg-gradient-to-br from-heat-2 to-heat-3',
  B: 'text-label bg-heat-1/40',
  C: 'text-muted bg-white/5',
};

export function CriticalityMark({ c }: { c: Criticality }) {
  return (
    <span className={`data text-[11px] w-5 h-5 inline-grid place-items-center rounded ${CRIT_STYLE[c]}`} title={`Criticality ${c}`}>
      {c}
    </span>
  );
}
