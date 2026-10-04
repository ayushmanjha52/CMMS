import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import type { DegradingAsset, Page, Summary, WorkOrderListItem } from '../api/types';
import { WorkOrderTable } from '../components/WorkOrderTable';
import { fmtHours } from '../components/format';
import { IconAlert, IconBolt, IconBox, IconClock, IconFlame, IconTrendDown } from '../components/icons';
import { CriticalityMark } from '../components/marks';
import { Empty, Loading, PageTitle, Plate } from '../components/ui';

export function BoardPage() {
  const summary = useQuery({ queryKey: ['summary'], queryFn: () => api<Summary>('/api/analytics/summary') });
  const degrading = useQuery({ queryKey: ['degrading'], queryFn: () => api<DegradingAsset[]>('/api/analytics/degrading') });
  const open = useQuery({
    queryKey: ['work-orders', 'board'],
    queryFn: () => api<Page<WorkOrderListItem>>('/api/work-orders?status=OPEN&status=IN_PROGRESS&status=ON_HOLD&size=15'),
  });

  const s = summary.data;
  return (
    <>
      <PageTitle kicker={new Date().toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'long' })}>Maintenance board</PageTitle>

      <div className="grid grid-cols-2 lg:grid-cols-5 gap-3 mb-5">
        <Counter label="Emergency open" value={s?.openByPriority.EMERGENCY} tone={s && s.openByPriority.EMERGENCY > 0 ? 'danger' : 'calm'} icon={IconFlame} link="/work-orders" />
        <Counter label="High priority open" value={s?.openByPriority.HIGH} tone={s && s.openByPriority.HIGH > 0 ? 'warning' : 'calm'} icon={IconBolt} link="/work-orders" />
        <Counter label="Overdue" value={s?.overdue} tone={s && s.overdue > 0 ? 'warning' : 'safe'} icon={IconClock} link="/pm" />
        <Counter label="Breakdowns · 30 days" value={s?.breakdownsLast30Days} tone="brand" icon={IconAlert} />
        <Counter label="Spares at reorder" value={s?.lowStockParts} tone={s && s.lowStockParts > 0 ? 'warning' : 'safe'} icon={IconBox} link="/spares" />
      </div>

      <section className="mb-5">
        <div className="flex items-center gap-2 mb-3">
          <IconTrendDown className="w-5 h-5 text-heat-4" />
          <h2 className="stencil text-[15px] text-white">Watch list</h2>
          <span className="text-muted text-[13px]">assets whose gap between failures is shrinking</span>
        </div>
        {degrading.isLoading ? (
          <Loading />
        ) : degrading.data && degrading.data.length > 0 ? (
          <div className="grid sm:grid-cols-2 xl:grid-cols-3 gap-3">
            {degrading.data.map((d) => (
              <WatchCard key={d.assetId} d={d} />
            ))}
          </div>
        ) : (
          <div className="plate">
            <Empty>No asset shows a shrinking gap between failures. The plant is running cold.</Empty>
          </div>
        )}
      </section>

      <Plate title="Outstanding work" right={<Link to="/work-orders" className="stencil text-[12px] hover:text-white">All work orders →</Link>}>
        {open.isLoading ? (
          <Loading />
        ) : open.data && open.data.content.length > 0 ? (
          <WorkOrderTable rows={open.data.content} />
        ) : (
          <Empty>No outstanding work orders. Breakdowns raised anywhere in the plant will appear here.</Empty>
        )}
      </Plate>
    </>
  );
}

/** A degrading asset: how much shorter the gap between failures has become, as a heat bar. */
function WatchCard({ d }: { d: DegradingAsset }) {
  const ratio = d.olderMeanGapHours > 0 ? d.recentMeanGapHours / d.olderMeanGapHours : 1;
  const drop = Math.round((1 - ratio) * 100);
  return (
    <Link
      to={`/assets/${d.assetId}`}
      className="group relative block rounded-xl border border-heat-3/40 p-4 overflow-hidden transition hover:-translate-y-0.5 hover:border-heat-4/70"
      style={{ background: 'radial-gradient(120% 140% at 100% 0%, rgba(255,61,127,0.28), transparent 55%), radial-gradient(90% 120% at 0% 100%, rgba(123,47,247,0.30), transparent 60%), linear-gradient(180deg, rgba(36,25,78,0.95), rgba(22,15,48,0.95))' }}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="data text-tag truncate">{d.tag}</div>
          <div className="text-white text-[15px] font-semibold truncate mt-0.5">{d.name}</div>
        </div>
        <CriticalityMark c={d.criticality} />
      </div>
      <div className="flex items-end justify-between mt-4">
        <div>
          <div className="stencil text-[11px] text-muted">MTBF change</div>
          <div className="data text-[30px] leading-none heat-text font-medium">−{drop}%</div>
        </div>
        <div className="text-right">
          <div className="data text-warning">{fmtHours(d.recentMeanGapHours)}</div>
          <div className="data text-muted text-[11.5px]">was {fmtHours(d.olderMeanGapHours)}</div>
        </div>
      </div>
      {/* Remaining gap as a fraction of the old one: a short, hot bar is a machine in trouble. */}
      <div className="mt-3 h-2 rounded-full bg-white/[0.06] overflow-hidden">
        <div className="h-full rounded-full heat-bar" style={{ width: `${Math.max(6, Math.round(ratio * 100))}%` }} />
      </div>
      <div className="mt-2 text-[12px] text-muted">
        {d.failures} failures in 12 months · <span className="text-heat-4 group-hover:underline">open health strip →</span>
      </div>
    </Link>
  );
}

// Status tones only when the number says something is wrong; otherwise calm brand colour.
const TONE = {
  danger: { glow: 'rgba(255,77,109,0.35)', border: 'border-danger/50', text: 'text-danger', icon: 'text-danger' },
  warning: { glow: 'rgba(255,166,43,0.30)', border: 'border-warning/50', text: 'text-warning', icon: 'text-warning' },
  safe: { glow: 'rgba(46,230,166,0.22)', border: 'border-safe/40', text: 'text-safe', icon: 'text-safe' },
  brand: { glow: 'rgba(192,38,211,0.30)', border: 'border-heat-2/50', text: 'heat-text', icon: 'text-heat-3' },
  calm: { glow: 'rgba(123,47,247,0.25)', border: 'border-engrave', text: 'text-label', icon: 'text-muted' },
} as const;

function Counter({ label, value, tone, icon: Icon, link }: {
  label: string;
  value: number | undefined;
  tone: keyof typeof TONE;
  icon: typeof IconBox;
  link?: string;
}) {
  const t = TONE[tone];
  const body = (
    <div
      className={`relative h-full rounded-xl border ${t.border} px-4 py-3 overflow-hidden transition-transform hover:-translate-y-0.5`}
      style={{ background: `radial-gradient(140% 120% at 100% 0%, ${t.glow}, transparent 60%), linear-gradient(180deg, rgba(36,25,78,0.95), rgba(22,15,48,0.95))` }}
    >
      <div className="flex items-start justify-between">
        <div className="stencil text-[11.5px] text-muted">{label}</div>
        <Icon className={`w-5 h-5 ${t.icon}`} />
      </div>
      <div className={`data text-[36px] leading-none mt-2 font-medium ${t.text}`}>{value ?? '—'}</div>
    </div>
  );
  return link ? <Link to={link} className="block">{body}</Link> : body;
}
