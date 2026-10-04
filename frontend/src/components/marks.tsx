import { Link } from 'react-router-dom';
import type { Criticality, PmState, Priority, WorkOrderStatus, WorkOrderType } from '../api/types';

/**
 * The asset tag: one fixed-format identifier threading through the whole app. Same look,
 * same destination, everywhere it appears.
 */
export function AssetTag({ id, tag, className = '' }: { id: string; tag: string; className?: string }) {
  return (
    <Link
      to={`/assets/${id}`}
      className={`data text-label underline decoration-engrave underline-offset-4 hover:decoration-label ${className}`}
      title={`Asset ${tag}`}
    >
      {tag}
    </Link>
  );
}

// Colour carries meaning before text does, as on a safety sign. MEDIUM and LOW are
// deliberately uncoloured: if everything is coloured, nothing is.
const PRIORITY_STYLE: Record<Priority, string> = {
  EMERGENCY: 'bg-danger text-white border-danger',
  HIGH: 'bg-warning text-surface border-warning',
  MEDIUM: 'text-label border-engrave',
  LOW: 'text-muted border-engrave',
};

export function PriorityMark({ priority }: { priority: Priority }) {
  return (
    <span className={`stencil text-[11px] px-1.5 h-5 inline-flex items-center border rounded ${PRIORITY_STYLE[priority]}`}>
      {priority === 'EMERGENCY' ? 'EMERG' : priority}
    </span>
  );
}

export function TypeMark({ type }: { type: WorkOrderType }) {
  return type === 'BREAKDOWN' ? (
    <span className="stencil text-[11px] text-danger" title="Breakdown">
      BD
    </span>
  ) : (
    <span className="stencil text-[11px] text-info" title="Preventive">
      PM
    </span>
  );
}

const STATUS_LABEL: Record<WorkOrderStatus, string> = {
  OPEN: 'Open',
  IN_PROGRESS: 'In progress',
  ON_HOLD: 'On hold',
  COMPLETED: 'Returned to service',
  CLOSED: 'Closed',
  CANCELLED: 'Cancelled',
};

export function StatusLabel({ status, overdue }: { status: WorkOrderStatus; overdue?: boolean }) {
  const color =
    status === 'COMPLETED' || status === 'CLOSED'
      ? 'text-safe'
      : status === 'CANCELLED'
        ? 'text-muted line-through'
        : overdue
          ? 'text-warning'
          : 'text-label';
  return (
    <span className={`stencil text-[12px] ${color}`}>
      {STATUS_LABEL[status]}
      {overdue && status !== 'COMPLETED' && status !== 'CLOSED' ? ' · overdue' : ''}
    </span>
  );
}

const PM_STYLE: Record<PmState, { label: string; cls: string }> = {
  OVERDUE: { label: 'Overdue', cls: 'bg-warning text-surface border-warning' },
  DUE: { label: 'Due', cls: 'text-warning border-warning' },
  APPROACHING: { label: 'Approaching', cls: 'text-caution border-caution' },
  SCHEDULED: { label: 'Scheduled', cls: 'text-info border-info' },
  OK: { label: 'OK', cls: 'text-safe border-engrave' },
  INACTIVE: { label: 'Inactive', cls: 'text-muted border-engrave' },
};

export function PmStateMark({ state }: { state: PmState }) {
  const s = PM_STYLE[state];
  return <span className={`stencil text-[11px] px-1.5 h-5 inline-flex items-center border rounded ${s.cls}`}>{s.label}</span>;
}

export function CriticalityMark({ c }: { c: Criticality }) {
  return (
    <span className={`data ${c === 'A' ? 'text-label' : 'text-muted'}`} title={`Criticality ${c}`}>
      {c}
    </span>
  );
}
