import { useMutation, useQuery } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { api, post } from '../api/client';
import type { AssetRef, FailureCodeView, Priority, UserView, WorkOrderDetail, WorkOrderType } from '../api/types';
import { useCan } from '../auth/AuthContext';
import { useTitle, useToast } from '../components/toast';
import { ErrorPlate, Field, PageTitle, Plate } from '../components/ui';

export function RaiseWorkOrderPage() {
  const can = useCan();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  useTitle(can.isTechnician ? 'Raise breakdown' : 'Raise work order');
  const toast = useToast();
  const [asset, setAsset] = useState<AssetRef | null>(null);
  const [q, setQ] = useState(params.get('tag') ?? '');
  const [type, setType] = useState<WorkOrderType>('BREAKDOWN');
  const [priority, setPriority] = useState<Priority>('HIGH');
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [failureCode, setFailureCode] = useState('');
  const [failedAt, setFailedAt] = useState('');
  const [assigneeId, setAssigneeId] = useState('');

  const search = useQuery({
    queryKey: ['asset-search', q],
    queryFn: () => api<AssetRef[]>(`/api/assets?q=${encodeURIComponent(q)}`),
    enabled: !asset && q.trim().length >= 2,
  });
  const codes = useQuery({ queryKey: ['failure-codes'], queryFn: () => api<FailureCodeView[]>('/api/work-orders/failure-codes') });
  const techs = useQuery({ queryKey: ['technicians'], queryFn: () => api<UserView[]>('/api/users?role=TECHNICIAN'), enabled: can.manageWork });

  const raise = useMutation({
    mutationFn: (body: unknown) => post<WorkOrderDetail>('/api/work-orders', body),
    onSuccess: (wo) => {
      toast(`${wo.number} raised on ${wo.asset.tag}`);
      navigate(`/work-orders/${wo.id}`);
    },
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    if (!asset) return;
    raise.mutate({
      assetId: asset.id,
      type: can.isTechnician ? 'BREAKDOWN' : type,
      priority,
      title,
      description: description || undefined,
      failureCode: failureCode || undefined,
      failedAt: failedAt ? new Date(failedAt).toISOString() : undefined,
      assigneeId: assigneeId || undefined,
    });
  }

  const isBreakdown = can.isTechnician || type === 'BREAKDOWN';

  return (
    <div className="max-w-3xl">
      <PageTitle>{can.isTechnician ? 'Raise breakdown' : 'Raise work order'}</PageTitle>
      <ErrorPlate error={raise.error} />
      <Plate>
        <form onSubmit={submit} className="p-4 grid sm:grid-cols-2 gap-4">
          <div className="sm:col-span-2">
            <Field label="Asset — search by tag or name">
              {asset ? (
                <div className="flex items-center gap-3 h-8">
                  <span className="data">{asset.tag}</span>
                  <span className="text-muted truncate">{asset.name}</span>
                  <button type="button" className="btn h-6 ml-auto" onClick={() => setAsset(null)}>
                    Change
                  </button>
                </div>
              ) : (
                <input className="field data" value={q} onChange={(e) => setQ(e.target.value)} placeholder="MTR-005 or conveyor" autoFocus />
              )}
            </Field>
            {!asset && search.data && (
              <ul className="border border-engrave border-t-0 rounded-b max-h-64 overflow-y-auto">
                {search.data.map((a) => (
                  <li key={a.id}>
                    <button type="button" className="w-full text-left px-2 h-8 flex gap-3 items-center hover:bg-surface border-b border-engrave" onClick={() => setAsset(a)}>
                      <span className="data">{a.tag}</span>
                      <span className="text-muted truncate">{a.name}</span>
                    </button>
                  </li>
                ))}
                {search.data.length === 0 && <li className="px-2 h-8 flex items-center text-muted">No matching asset.</li>}
              </ul>
            )}
          </div>

          {!can.isTechnician && (
            <Field label="Type">
              <select className="field" value={type} onChange={(e) => setType(e.target.value as WorkOrderType)}>
                <option value="BREAKDOWN">Breakdown</option>
                <option value="PREVENTIVE">Preventive / planned</option>
              </select>
            </Field>
          )}
          <Field label="Priority">
            <select className="field" value={priority} onChange={(e) => setPriority(e.target.value as Priority)}>
              <option value="EMERGENCY">Emergency</option>
              <option value="HIGH">High</option>
              <option value="MEDIUM">Medium</option>
              <option value="LOW">Low</option>
            </select>
          </Field>
          <div className="sm:col-span-2">
            <Field label="What is wrong">
              <input className="field" value={title} onChange={(e) => setTitle(e.target.value)} maxLength={200} required placeholder="Motor tripped on overload" />
            </Field>
          </div>
          <div className="sm:col-span-2">
            <Field label="Details">
              <textarea className="field h-24 py-1" value={description} onChange={(e) => setDescription(e.target.value)} />
            </Field>
          </div>
          {isBreakdown && (
            <>
              <Field label="Failed at (blank = now)">
                <input className="field data" type="datetime-local" value={failedAt} onChange={(e) => setFailedAt(e.target.value)} />
              </Field>
              <Field label="Failure code (if known)">
                <select className="field" value={failureCode} onChange={(e) => setFailureCode(e.target.value)}>
                  <option value="">Not yet known</option>
                  {codes.data?.map((c) => (
                    <option key={c.code} value={c.code}>
                      {c.code} — {c.description}
                    </option>
                  ))}
                </select>
              </Field>
            </>
          )}
          {can.manageWork && (
            <Field label="Assign to">
              <select className="field" value={assigneeId} onChange={(e) => setAssigneeId(e.target.value)}>
                <option value="">Leave unassigned</option>
                {techs.data?.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.fullName} · {t.trade} · shift {t.shift}
                  </option>
                ))}
              </select>
            </Field>
          )}
          {can.isTechnician && <p className="sm:col-span-2 text-muted text-[13px]">A breakdown you raise is assigned to you.</p>}
          <div className="sm:col-span-2 flex gap-2">
            <button className="btn btn-go" disabled={!asset || raise.isPending}>
              {raise.isPending ? 'Raising…' : 'Raise work order'}
            </button>
            <button type="button" className="btn" onClick={() => navigate(-1)}>
              Back
            </button>
          </div>
        </form>
      </Plate>
    </div>
  );
}
