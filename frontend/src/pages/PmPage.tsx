import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { api, post } from '../api/client';
import type { PmSchedule } from '../api/types';
import { useCan } from '../auth/AuthContext';
import { fmtDate, fmtHours } from '../components/format';
import { AssetTag, PmStateMark, PriorityMark } from '../components/marks';
import { Empty, ErrorPlate, Loading, PageTitle, Plate, Scroll } from '../components/ui';

export function PmPage() {
  const can = useCan();
  const qc = useQueryClient();
  const list = useQuery({ queryKey: ['pm'], queryFn: () => api<PmSchedule[]>('/api/pm-schedules') });
  const run = useMutation({
    mutationFn: () => post<{ generated: number }>('/api/pm-schedules/run'),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['pm'] });
      qc.invalidateQueries({ queryKey: ['work-orders'] });
    },
  });

  return (
    <>
      <PageTitle
        right={
          can.manageWork && (
            <span className="flex items-center gap-3">
              {run.data && <span className="stencil text-[12px] text-muted">{run.data.generated} work orders generated</span>}
              <button className="btn" onClick={() => run.mutate()} disabled={run.isPending}>
                Generate due PMs now
              </button>
            </span>
          )
        }
      >
        Preventive maintenance
      </PageTitle>
      <p className="text-muted text-[13px] mb-3 max-w-3xl">
        Each schedule triggers on days or running hours, whichever is reached first, and is generated a tenth of its interval
        early so shutdown and spares can be planned. The generator also runs every 15 minutes.
      </p>
      <ErrorPlate error={list.error ?? run.error} />
      <Plate title="Schedules">
        {list.isLoading ? <Loading /> : !list.data?.length ? <Empty>No PM schedules set up.</Empty> : (
          <Scroll>
            <table className="grid-table">
              <thead>
                <tr>
                  <th>State</th>
                  <th>Asset</th>
                  <th>Task</th>
                  <th>Trade</th>
                  <th>Prio</th>
                  <th className="text-right">Every</th>
                  <th className="text-right">Last done</th>
                  <th className="text-right">Next due</th>
                  <th className="text-right">Hours left</th>
                  <th>Work order</th>
                </tr>
              </thead>
              <tbody>
                {list.data.map((p) => (
                  <tr key={p.id}>
                    <td><PmStateMark state={p.state} /></td>
                    <td><AssetTag id={p.assetId} tag={p.assetTag} /></td>
                    <td className="max-w-[18rem] truncate" title={p.title}>{p.title}</td>
                    <td className="stencil text-[12px] text-muted">{p.trade}</td>
                    <td><PriorityMark priority={p.priority} /></td>
                    <td className="data text-right">
                      {[p.intervalDays && `${p.intervalDays} d`, p.intervalRunningHours && `${Math.round(p.intervalRunningHours)} h`].filter(Boolean).join(' / ')}
                    </td>
                    <td className="data text-right text-muted">{fmtDate(p.lastDoneAt)}</td>
                    <td className="data text-right">{fmtDate(p.nextDueAt)}</td>
                    <td className={`data text-right ${p.runningHoursRemaining != null && p.runningHoursRemaining <= 0 ? 'text-warning' : ''}`}>
                      {p.runningHoursRemaining == null ? '—' : fmtHours(p.runningHoursRemaining)}
                    </td>
                    <td>
                      {p.openOrder ? (
                        <Link to={`/work-orders/${p.openOrder.id}`} className={`data hover:underline ${p.openOrder.overdue ? 'text-warning' : ''}`}>
                          {p.openOrder.number}
                        </Link>
                      ) : (
                        <span className="text-muted">—</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </Scroll>
        )}
      </Plate>
    </>
  );
}
