import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import type { DegradingAsset, Page, Summary, WorkOrderListItem } from '../api/types';
import { WorkOrderTable } from '../components/WorkOrderTable';
import { fmtHours } from '../components/format';
import { AssetTag, CriticalityMark } from '../components/marks';
import { Empty, Loading, PageTitle, Plate, Scroll } from '../components/ui';

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
      <PageTitle>Maintenance board</PageTitle>

      <div className="grid grid-cols-2 lg:grid-cols-5 gap-px bg-engrave border border-engrave rounded mb-4">
        <Counter label="Emergency open" value={s?.openByPriority.EMERGENCY} tone={s && s.openByPriority.EMERGENCY > 0 ? 'text-danger' : undefined} />
        <Counter label="High priority open" value={s?.openByPriority.HIGH} tone={s && s.openByPriority.HIGH > 0 ? 'text-warning' : undefined} />
        <Counter label="Overdue" value={s?.overdue} tone={s && s.overdue > 0 ? 'text-warning' : undefined} />
        <Counter label="Breakdowns, 30 days" value={s?.breakdownsLast30Days} />
        <Counter label="Spares at reorder" value={s?.lowStockParts} tone={s && s.lowStockParts > 0 ? 'text-warning' : undefined} link="/spares" />
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-[minmax(0,1fr)_minmax(0,32rem)] gap-4 items-start">
        <Plate title="Outstanding work" right={<Link to="/work-orders" className="stencil text-[12px] hover:text-label">All work orders →</Link>}>
          {open.isLoading ? (
            <Loading />
          ) : open.data && open.data.content.length > 0 ? (
            <WorkOrderTable rows={open.data.content} compact />
          ) : (
            <Empty>No outstanding work orders. Breakdowns raised anywhere in the plant will appear here.</Empty>
          )}
        </Plate>

        <Plate title="MTBF falling — last 12 months">
          {degrading.isLoading ? (
            <Loading />
          ) : degrading.data && degrading.data.length > 0 ? (
            <Scroll>
              <table className="grid-table">
                <thead>
                  <tr>
                    <th>Asset</th>
                    <th className="text-right">Gap now</th>
                    <th className="text-right">Was</th>
                  </tr>
                </thead>
                <tbody>
                  {degrading.data.map((d) => (
                    <tr key={d.assetId}>
                      <td>
                        <AssetTag id={d.assetId} tag={d.tag} />
                        <div className="text-muted text-[12px] leading-tight truncate max-w-[16rem]">
                          <CriticalityMark c={d.criticality} /> · {d.name}
                        </div>
                      </td>
                      <td className="data text-right text-warning">{fmtHours(d.recentMeanGapHours)}</td>
                      <td className="data text-right text-muted">{fmtHours(d.olderMeanGapHours)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </Scroll>
          ) : (
            <Empty>No asset shows a shrinking gap between failures.</Empty>
          )}
        </Plate>
      </div>
    </>
  );
}

function Counter({ label, value, tone, link }: { label: string; value: number | undefined; tone?: string; link?: string }) {
  const body = (
    <div className="bg-raised px-3 py-2 h-full">
      <div className="stencil text-[11px] text-muted">{label}</div>
      <div className={`data text-[26px] leading-tight ${tone ?? 'text-label'}`}>{value ?? '—'}</div>
    </div>
  );
  return link ? <Link to={link}>{body}</Link> : body;
}
