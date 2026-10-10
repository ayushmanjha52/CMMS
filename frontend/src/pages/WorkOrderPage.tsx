import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { useParams } from 'react-router-dom';
import { api, post } from '../api/client';
import type { FailureCodeView, SparePart, UserView, WorkOrderAction, WorkOrderDetail } from '../api/types';
import { useCan } from '../auth/AuthContext';
import { fmtDateTime, fmtHours, fmtMinutes, fmtMoney, fmtQty } from '../components/format';
import { AssetTag, PriorityMark, StatusLabel } from '../components/marks';
import { useTitle, useToast } from '../components/toast';
import { Empty, Engraved, ErrorPlate, Field, Loading, Plate, Scroll } from '../components/ui';

// Plant vocabulary on every button.
const ACTION_LABEL: Record<WorkOrderAction, string> = {
  START: 'Start work',
  HOLD: 'Put on hold',
  RETURN_TO_SERVICE: 'Return to service',
  CLOSE: 'Approve closure',
  REWORK: 'Send back for rework',
  CANCEL: 'Cancel work order',
};

// What the toast says once each action has taken effect.
const DONE_MESSAGE: Record<WorkOrderAction, string> = {
  START: 'Work started',
  HOLD: 'Work order put on hold',
  RETURN_TO_SERVICE: 'Equipment returned to service',
  CLOSE: 'Closure approved',
  REWORK: 'Sent back for rework',
  CANCEL: 'Work order cancelled',
};

const NOTE_PROMPT: Partial<Record<WorkOrderAction, { label: string; required: boolean }>> = {
  HOLD: { label: 'Hold reason (awaiting spares, shutdown permit…)', required: true },
  REWORK: { label: 'What needs reworking', required: true },
  CANCEL: { label: 'Reason for cancelling', required: true },
  CLOSE: { label: 'Closure remarks (optional)', required: false },
};

export function WorkOrderPage() {
  const { id = '' } = useParams();
  const can = useCan();
  const qc = useQueryClient();
  const key = ['work-order', id];
  const wo = useQuery({ queryKey: key, queryFn: () => api<WorkOrderDetail>(`/api/work-orders/${id}`) });
  const toast = useToast();
  useTitle(wo.data ? wo.data.number : 'Work order');

  const [pending, setPending] = useState<WorkOrderAction | null>(null);

  const mutate = useMutation({
    mutationFn: ({ path, body }: { path: string; body: unknown }) => post<WorkOrderDetail>(`/api/work-orders/${id}${path}`, body),
    onSuccess: (detail, vars) => {
      const body = vars.body as { action?: WorkOrderAction };
      toast(
        vars.path === '/transitions' && body.action ? `${DONE_MESSAGE[body.action]} · ${detail.number}`
          : vars.path === '/labour' ? `Labour booked · total ${fmtMinutes(detail.labourMinutesTotal)}`
          : vars.path === '/parts' ? `Spares drawn · job cost ${fmtMoney(detail.partsCostTotal)}`
          : `Assigned to ${detail.assignee?.name ?? 'technician'}`,
      );
      qc.setQueryData(key, detail);
      qc.invalidateQueries({ queryKey: ['work-orders'] });
      qc.invalidateQueries({ queryKey: ['summary'] });
      setPending(null);
    },
  });

  if (wo.isLoading) return <Loading />;
  if (wo.error || !wo.data) return <ErrorPlate error={wo.error ?? 'Work order not found'} />;
  const w = wo.data;
  const workable = w.status === 'IN_PROGRESS' || w.status === 'ON_HOLD';

  function act(action: WorkOrderAction) {
    const needsCode = action === 'RETURN_TO_SERVICE' && w.type === 'BREAKDOWN' && !w.failureCode;
    if (NOTE_PROMPT[action] || needsCode) {
      setPending(action);
      mutate.reset();
    } else {
      mutate.mutate({ path: '/transitions', body: { action } });
    }
  }

  return (
    <div className="space-y-4 max-w-6xl">
      {/* key={status}: the header re-mounts on a state change and plays the one animation in the app. */}
      <section key={w.status} className="plate state-changed">
        <div className="px-3 py-3 border-b border-engrave">
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
            <PriorityMark priority={w.priority} />
            <span className="data text-[15px]">{w.number}</span>
            <span className={`stencil text-[12px] ${w.type === 'BREAKDOWN' ? 'text-danger' : 'text-info'}`}>
              {w.type === 'BREAKDOWN' ? 'Breakdown' : 'Preventive'}
            </span>
            <StatusLabel status={w.status} overdue={w.overdue} />
          </div>
          <h1 className="font-display text-[34px] sm:text-[40px] leading-[1.05] text-white mt-2">{w.title}</h1>
          <div className="mt-1 flex flex-wrap items-center gap-x-3 text-muted">
            <AssetTag id={w.asset.id} tag={w.asset.tag} className="text-[14px]" />
            <span>{w.asset.name}</span>
          </div>
        </div>

        {w.availableActions.length > 0 && (
          <div className="px-3 py-2 flex flex-wrap gap-2 border-b border-engrave">
            {w.availableActions.map((a) => (
              <button
                key={a}
                className={`btn ${a === 'RETURN_TO_SERVICE' || a === 'CLOSE' ? 'btn-go' : ''}`}
                disabled={mutate.isPending}
                onClick={() => act(a)}
              >
                {ACTION_LABEL[a]}
              </button>
            ))}
          </div>
        )}
        {pending && <TransitionForm action={pending} wo={w} busy={mutate.isPending} onCancel={() => setPending(null)} onSubmit={(body) => mutate.mutate({ path: '/transitions', body })} />}
        {mutate.error && (
          <div className="px-3 pt-3">
            <ErrorPlate error={mutate.error} />
          </div>
        )}

        <div className="grid grid-cols-2 md:grid-cols-4 border-l border-engrave -mb-px">
          <Engraved label="Raised">{fmtDateTime(w.raisedAt)}</Engraved>
          <Engraved label="Started">{fmtDateTime(w.startedAt)}</Engraved>
          <Engraved label="Returned to service">{fmtDateTime(w.completedAt)}</Engraved>
          <Engraved label="Closed">{fmtDateTime(w.closedAt)}</Engraved>
          <Engraved label="Downtime from">{fmtDateTime(w.downtimeStart)}</Engraved>
          <Engraved label="Downtime to">{w.downtimeStart && !w.downtimeEnd ? <span className="text-danger">Still down</span> : fmtDateTime(w.downtimeEnd)}</Engraved>
          <Engraved label="Downtime">{fmtMinutes(w.downtimeMinutes)}</Engraved>
          <Engraved label="Failure code">{w.failureCode ? `${w.failureCode.code} ${w.failureCode.description}` : '—'}</Engraved>
          {w.pmScheduleId && (
            <>
              <Engraved label="Due by date">
                <span className={w.overdue ? 'text-warning' : ''}>{fmtDateTime(w.dueAt)}</span>
              </Engraved>
              <Engraved label="Due by meter">{fmtHours(w.dueRunningHours)}</Engraved>
            </>
          )}
        </div>
      </section>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 items-start">
        <div className="space-y-4">
          <Plate title="Job">
            <div className="p-3 space-y-2 text-[14px]">
              {w.description ? <p className="whitespace-pre-line">{w.description}</p> : <p className="text-muted">No description.</p>}
              {w.holdReason && (
                <p>
                  <span className="stencil text-[12px] text-warning mr-2">On hold</span>
                  {w.holdReason}
                </p>
              )}
              {w.closureNote && (
                <p>
                  <span className="stencil text-[12px] text-muted mr-2">Remarks</span>
                  {w.closureNote}
                </p>
              )}
            </div>
          </Plate>
          <AssigneePanel wo={w} canAssign={can.manageWork} busy={mutate.isPending} onAssign={(technicianId) => mutate.mutate({ path: '/assign', body: { technicianId } })} />
        </div>

        <div className="space-y-4">
          <Plate title="Labour" right={<span className="data text-label">{fmtMinutes(w.labourMinutesTotal)}</span>}>
            {w.labour.length === 0 ? (
              <Empty>No labour booked. Hours must be recorded before the equipment is returned to service.</Empty>
            ) : (
              <Scroll>
                <table className="grid-table">
                  <tbody>
                    {w.labour.map((l) => (
                      <tr key={l.id}>
                        <td className="data text-muted">{l.workDate}</td>
                        <td>{l.technician?.name}</td>
                        <td className="data text-right">{fmtMinutes(l.minutes)}</td>
                        <td className="text-muted truncate max-w-[14rem]">{l.note}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </Scroll>
            )}
            {workable && can.doWork && <LabourForm busy={mutate.isPending} onSubmit={(body) => mutate.mutate({ path: '/labour', body })} />}
          </Plate>

          <Plate title="Spares drawn" right={<span className="data text-label">{fmtMoney(w.partsCostTotal)}</span>}>
            {w.parts.length === 0 ? (
              <Empty>No spares drawn against this job.</Empty>
            ) : (
              <Scroll>
                <table className="grid-table">
                  <tbody>
                    {w.parts.map((p) => (
                      <tr key={p.id}>
                        <td className="data">{p.partNumber}</td>
                        <td className="text-muted truncate max-w-[12rem]">{p.description}</td>
                        <td className="data text-right">
                          {fmtQty(p.quantity)} {p.unit}
                        </td>
                        <td className="data text-right">{fmtMoney(p.lineCost)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </Scroll>
            )}
            {workable && can.doWork && <PartForm busy={mutate.isPending} onSubmit={(body) => mutate.mutate({ path: '/parts', body })} />}
          </Plate>
        </div>
      </div>
    </div>
  );
}

function TransitionForm({ action, wo, busy, onCancel, onSubmit }: {
  action: WorkOrderAction;
  wo: WorkOrderDetail;
  busy: boolean;
  onCancel: () => void;
  onSubmit: (body: unknown) => void;
}) {
  const prompt = NOTE_PROMPT[action];
  const needsCode = action === 'RETURN_TO_SERVICE' && wo.type === 'BREAKDOWN' && !wo.failureCode;
  const codes = useQuery({ queryKey: ['failure-codes'], queryFn: () => api<FailureCodeView[]>('/api/work-orders/failure-codes'), enabled: needsCode });
  const [note, setNote] = useState('');
  const [code, setCode] = useState('');

  function submit(e: FormEvent) {
    e.preventDefault();
    onSubmit({ action, note: note || undefined, failureCode: code || undefined });
  }

  return (
    <form onSubmit={submit} className="px-3 py-3 border-b border-engrave flex flex-wrap items-end gap-3 bg-surface">
      {needsCode && (
        <Field label="What failed">
          <select className="field min-w-[18rem]" value={code} onChange={(e) => setCode(e.target.value)} required>
            <option value="">Select failure code…</option>
            {codes.data?.map((c) => (
              <option key={c.code} value={c.code}>
                {c.code} — {c.description}
              </option>
            ))}
          </select>
        </Field>
      )}
      {prompt && (
        <div className="flex-1 min-w-[16rem]">
          <Field label={prompt.label}>
            <input className="field" value={note} onChange={(e) => setNote(e.target.value)} required={prompt.required} autoFocus />
          </Field>
        </div>
      )}
      <button className="btn btn-go" disabled={busy}>
        {ACTION_LABEL[action]}
      </button>
      <button type="button" className="btn" onClick={onCancel}>
        Back
      </button>
    </form>
  );
}

function AssigneePanel({ wo, canAssign, busy, onAssign }: { wo: WorkOrderDetail; canAssign: boolean; busy: boolean; onAssign: (id: string) => void }) {
  const outstanding = wo.status === 'OPEN' || wo.status === 'IN_PROGRESS' || wo.status === 'ON_HOLD';
  const techs = useQuery({
    queryKey: ['technicians'],
    queryFn: () => api<UserView[]>('/api/users?role=TECHNICIAN'),
    enabled: canAssign && outstanding,
  });
  const [choice, setChoice] = useState('');
  return (
    <Plate title="Assigned to">
      <div className="p-3 flex flex-wrap items-center gap-3">
        <span className={wo.assignee ? '' : 'stencil text-[13px] text-warning'}>
          {wo.assignee ? `${wo.assignee.name}` : 'Unassigned'}
          {wo.assignee?.trade && <span className="stencil text-[12px] text-muted ml-2">{wo.assignee.trade}</span>}
        </span>
        {canAssign && outstanding && (
          <span className="flex gap-2 ml-auto">
            <select className="field w-auto" value={choice} onChange={(e) => setChoice(e.target.value)}>
              <option value="">Choose technician…</option>
              {techs.data?.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.fullName} · {t.trade} · shift {t.shift}
                </option>
              ))}
            </select>
            <button className="btn" disabled={!choice || busy} onClick={() => onAssign(choice)}>
              Assign
            </button>
          </span>
        )}
      </div>
    </Plate>
  );
}

function LabourForm({ busy, onSubmit }: { busy: boolean; onSubmit: (body: unknown) => void }) {
  const [hours, setHours] = useState('');
  const [mins, setMins] = useState('');
  const [note, setNote] = useState('');
  // Local calendar date, not UTC: at 02:00 in India, toISOString() still says yesterday.
  const today = new Date().toLocaleDateString('en-CA');
  const [workDate, setWorkDate] = useState(today);

  function submit(e: FormEvent) {
    e.preventDefault();
    const minutes = (parseInt(hours || '0', 10) || 0) * 60 + (parseInt(mins || '0', 10) || 0);
    if (minutes <= 0) return;
    onSubmit({ minutes, workDate, note: note || undefined });
    setHours('');
    setMins('');
    setNote('');
  }

  return (
    <form onSubmit={submit} className="p-3 border-t border-engrave flex flex-wrap items-end gap-2">
      <Field label="Hours">
        <input className="field data w-16" inputMode="numeric" value={hours} onChange={(e) => setHours(e.target.value.replace(/\D/g, ''))} />
      </Field>
      <Field label="Min">
        <input className="field data w-16" inputMode="numeric" value={mins} onChange={(e) => setMins(e.target.value.replace(/\D/g, ''))} />
      </Field>
      <Field label="Date">
        <input className="field data w-36" type="date" max={today} value={workDate} onChange={(e) => setWorkDate(e.target.value)} />
      </Field>
      <div className="flex-1 min-w-[10rem]">
        <Field label="Note">
          <input className="field" value={note} onChange={(e) => setNote(e.target.value)} />
        </Field>
      </div>
      <button className="btn" disabled={busy || (!hours && !mins)}>
        Book labour
      </button>
    </form>
  );
}

function PartForm({ busy, onSubmit }: { busy: boolean; onSubmit: (body: unknown) => void }) {
  const parts = useQuery({ queryKey: ['parts'], queryFn: () => api<SparePart[]>('/api/parts') });
  const [partId, setPartId] = useState('');
  const [qty, setQty] = useState('1');
  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        onSubmit({ sparePartId: partId, quantity: qty });
      }}
      className="p-3 border-t border-engrave flex flex-wrap items-end gap-2"
    >
      <div className="flex-1 min-w-[14rem]">
        <Field label="Spare">
          <select className="field" value={partId} onChange={(e) => setPartId(e.target.value)} required>
            <option value="">Choose spare…</option>
            {parts.data?.map((p) => (
              <option key={p.id} value={p.id}>
                {p.partNumber} — {p.description} ({fmtQty(p.stockQty)} {p.unit} in stock)
              </option>
            ))}
          </select>
        </Field>
      </div>
      <Field label="Qty">
        <input className="field data w-20" inputMode="decimal" value={qty} onChange={(e) => setQty(e.target.value.replace(/[^\d.]/g, ''))} required />
      </Field>
      <button className="btn" disabled={busy || !partId}>
        Draw from store
      </button>
    </form>
  );
}
