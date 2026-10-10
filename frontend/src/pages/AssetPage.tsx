import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api, post } from '../api/client';
import type { AssetDetail, AssetLevel, HealthStrip as Strip, Page, WorkOrderListItem } from '../api/types';
import { useCan } from '../auth/AuthContext';
import { HealthStrip } from '../components/HealthStrip';
import { WorkOrderTable } from '../components/WorkOrderTable';
import { fmtDate, fmtDateTime, fmtHours, fmtMinutes } from '../components/format';
import { AssetTag } from '../components/marks';
import { useTitle, useToast } from '../components/toast';
import { Empty, Engraved, ErrorPlate, Field, Loading, Plate } from '../components/ui';

export function AssetPage() {
  const { id = '' } = useParams();
  const can = useCan();
  const asset = useQuery({ queryKey: ['asset', id], queryFn: () => api<AssetDetail>(`/api/assets/${id}`) });
  useTitle(asset.data ? `${asset.data.code} ${asset.data.name}` : 'Asset');
  const strip = useQuery({
    queryKey: ['health-strip', id],
    queryFn: () => api<Strip>(`/api/analytics/assets/${id}/health-strip`),
    enabled: can.seeAnalytics,
  });
  const history = useQuery({
    queryKey: ['work-orders', 'asset', id],
    queryFn: () => api<Page<WorkOrderListItem>>(`/api/work-orders?assetId=${id}&size=25`),
  });

  if (asset.isLoading) return <Loading />;
  if (asset.error || !asset.data) return <ErrorPlate error={asset.error ?? 'Asset not found'} />;
  const a = asset.data;
  const s = strip.data?.summary;
  const metered = a.level === 'MACHINE' || a.level === 'COMPONENT';

  return (
    <div className="space-y-4 max-w-6xl">
      <nav className="data text-muted flex flex-wrap items-center gap-1" aria-label="Asset path">
        {a.path.map((p) => (
          <span key={p.id} className="flex items-center gap-1">
            <AssetTag id={p.id} tag={p.tag.split('/').pop()!} className="text-muted" />
            <span>/</span>
          </span>
        ))}
      </nav>

      {/* The nameplate: stamped-metal grid of engraved labels. */}
      <section className="plate">
        <div className="px-3 py-3 border-b border-engrave flex flex-wrap items-center gap-x-4 gap-y-1">
          <span className="data text-[16px]">{a.tag}</span>
          <h1 className="font-display text-[34px] sm:text-[40px] leading-none text-white">{a.name}</h1>
          <span className="stencil text-[12px] text-muted">{a.level}</span>
          {!a.inService && <span className="stencil text-[12px] text-muted border border-engrave px-1.5">Decommissioned</span>}
          {can.doWork && (
            <Link to={`/work-orders/raise?tag=${encodeURIComponent(a.code)}`} className="btn btn-go ml-auto">
              Raise work order
            </Link>
          )}
        </div>
        <div className="grid grid-cols-2 md:grid-cols-4 border-l border-engrave -mb-px">
          <Engraved label="Make">{a.make}</Engraved>
          <Engraved label="Model">{a.model}</Engraved>
          <Engraved label="Rating">{a.rating}</Engraved>
          <Engraved label="Serial no.">{a.serialNumber}</Engraved>
          <Engraved label="Criticality">{a.criticality}</Engraved>
          <Engraved label="Commissioned">{fmtDate(a.commissionedOn)}</Engraved>
          <Engraved label="Running hours">{metered ? fmtHours(a.runningHours) : '—'}</Engraved>
          <Engraved label="Meter read">{metered ? fmtDateTime(a.runningUpdatedAt) : '—'}</Engraved>
        </div>
      </section>

      {can.seeAnalytics && (metered || (strip.data && strip.data.failures.length > 0)) && (
        <Plate title="Health — last 12 months">
          {strip.isLoading ? <Loading /> : strip.error ? <ErrorPlate error={strip.error} /> : strip.data && (
            <>
              <HealthStrip strip={strip.data} />
              <div className="grid grid-cols-2 md:grid-cols-5 border-l border-t border-engrave -mb-px">
                <Engraved label="MTBF">{s?.mtbfHours == null ? 'No failures' : fmtHours(s.mtbfHours)}</Engraved>
                <Engraved label="MTTR">{fmtHours(s?.mttrHours)}</Engraved>
                <Engraved label="Availability">{s?.availabilityPercent == null ? '—' : `${s.availabilityPercent.toFixed(2)} %`}</Engraved>
                <Engraved label="Failures">{s?.failures}</Engraved>
                <Engraved label="Breakdown downtime">{fmtMinutes(s?.downtimeMinutes)}</Engraved>
              </div>
            </>
          )}
        </Plate>
      )}

      <div className="grid grid-cols-1 2xl:grid-cols-[minmax(0,1fr)_minmax(0,22rem)] gap-4 items-start">
        <Plate title="Work order history">
          {history.isLoading ? <Loading /> : history.data && history.data.content.length > 0 ? (
            <WorkOrderTable rows={history.data.content} hideAsset />
          ) : (
            <Empty>No work orders raised against this asset yet.</Empty>
          )}
        </Plate>

        <div className="space-y-4">
          {metered && can.doWork && <MeterForm asset={a} />}
          <Plate title={`Equipment under ${a.code}`}>
            {a.children.length === 0 ? <Empty>Nothing registered below this asset.</Empty> : (
              <ul>
                {a.children.map((c) => (
                  <li key={c.id} className="flex items-center gap-2 px-3 h-8 border-b border-engrave last:border-b-0">
                    <AssetTag id={c.id} tag={c.tag.split('/').pop()!} />
                    <span className="truncate">{c.name}</span>
                  </li>
                ))}
              </ul>
            )}
          </Plate>
          {can.admin && a.level !== 'COMPONENT' && <RegisterChild parent={a} />}
        </div>
      </div>
    </div>
  );
}

function MeterForm({ asset }: { asset: AssetDetail }) {
  const qc = useQueryClient();
  const toast = useToast();
  const [hours, setHours] = useState('');
  const m = useMutation({
    mutationFn: () => post<AssetDetail>(`/api/assets/${asset.id}/meter-readings`, { runningHours: hours }),
    onSuccess: (d) => {
      qc.setQueryData(['asset', asset.id], d);
      toast(`Meter reading recorded · ${fmtHours(d.runningHours)}`);
      setHours('');
    },
  });
  return (
    <Plate title="Hour-meter reading">
      <form
        className="p-3 flex items-end gap-2"
        onSubmit={(e: FormEvent) => {
          e.preventDefault();
          m.mutate();
        }}
      >
        <div className="flex-1">
          <Field label={`Current ${fmtHours(asset.runningHours)}`}>
            <input className="field data" inputMode="decimal" value={hours} onChange={(e) => setHours(e.target.value.replace(/[^\d.]/g, ''))} placeholder="e.g. 28450.5" required />
          </Field>
        </div>
        <button className="btn" disabled={m.isPending}>
          Record
        </button>
      </form>
      {m.error && (
        <div className="px-3">
          <ErrorPlate error={m.error} />
        </div>
      )}
    </Plate>
  );
}

const CHILD_LEVELS: Record<AssetLevel, AssetLevel[]> = {
  PLANT: ['AREA', 'LINE', 'MACHINE', 'COMPONENT'],
  AREA: ['LINE', 'MACHINE', 'COMPONENT'],
  LINE: ['MACHINE', 'COMPONENT'],
  MACHINE: ['COMPONENT'],
  COMPONENT: ['COMPONENT'],
};

function RegisterChild({ parent }: { parent: AssetDetail }) {
  const qc = useQueryClient();
  const toast = useToast();
  const levels = CHILD_LEVELS[parent.level];
  const [level, setLevel] = useState<AssetLevel>(levels[0]);
  const [form, setForm] = useState({ name: '', typePrefix: '', make: '', model: '', rating: '', serialNumber: '', criticality: 'B' });
  const set = (k: keyof typeof form) => (e: { target: { value: string } }) => setForm((f) => ({ ...f, [k]: e.target.value }));
  const needsPrefix = level === 'MACHINE' || level === 'COMPONENT';
  const m = useMutation({
    mutationFn: () => post<AssetDetail>('/api/assets', { parentId: parent.id, level, ...form, typePrefix: needsPrefix ? form.typePrefix : undefined }),
    onSuccess: (created) => {
      toast(`Registered ${created.tag}`);
      qc.invalidateQueries({ queryKey: ['asset', parent.id] });
      qc.invalidateQueries({ queryKey: ['asset-tree'] });
      setForm({ name: '', typePrefix: '', make: '', model: '', rating: '', serialNumber: '', criticality: 'B' });
    },
  });
  return (
    <Plate title="Register equipment here">
      <form
        className="p-3 grid grid-cols-2 gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          m.mutate();
        }}
      >
        {m.error && <div className="col-span-2"><ErrorPlate error={m.error} /></div>}
        <Field label="Level">
          <select className="field" value={level} onChange={(e) => setLevel(e.target.value as AssetLevel)}>
            {levels.map((l) => <option key={l}>{l}</option>)}
          </select>
        </Field>
        <Field label={needsPrefix ? 'Type prefix (MTR, PMP…)' : 'Tag prefix'}>
          {needsPrefix ? (
            <input className="field data uppercase" maxLength={5} value={form.typePrefix} onChange={set('typePrefix')} required />
          ) : (
            <input className="field data" value={level === 'AREA' ? 'AREA' : level === 'LINE' ? 'LN' : ''} disabled />
          )}
        </Field>
        <div className="col-span-2"><Field label="Name"><input className="field" value={form.name} onChange={set('name')} required /></Field></div>
        <Field label="Make"><input className="field" value={form.make} onChange={set('make')} /></Field>
        <Field label="Model"><input className="field" value={form.model} onChange={set('model')} /></Field>
        <Field label="Rating"><input className="field" value={form.rating} onChange={set('rating')} /></Field>
        <Field label="Serial no."><input className="field" value={form.serialNumber} onChange={set('serialNumber')} /></Field>
        <Field label="Criticality">
          <select className="field" value={form.criticality} onChange={set('criticality')}>
            <option value="A">A — stops production / safety</option>
            <option value="B">B — standby exists</option>
            <option value="C">C — run to failure</option>
          </select>
        </Field>
        <div className="flex items-end">
          <button className="btn btn-go w-full justify-center" disabled={m.isPending}>Register</button>
        </div>
      </form>
    </Plate>
  );
}
