import { Link } from 'react-router-dom';
import type { WorkOrderListItem } from '../api/types';
import { fmtDateTime } from './format';
import { AssetTag, PriorityMark, StatusLabel, TypeMark } from './marks';
import { Scroll } from './ui';

/** `hideAsset` on an asset's own page, where repeating its tag on every row adds nothing. */
export function WorkOrderTable({ rows, compact = false, hideAsset = false }: { rows: WorkOrderListItem[]; compact?: boolean; hideAsset?: boolean }) {
  return (
    <Scroll>
      <table className="grid-table">
        <thead>
          <tr>
            <th className="w-16">Prio</th>
            <th>Number</th>
            <th className="w-8"></th>
            <th>Title</th>
            {!hideAsset && <th>Asset</th>}
            {!compact && <th>Assignee</th>}
            <th>Status</th>
            <th className="text-right">Raised</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((w) => (
            <tr key={w.id}>
              <td>
                <PriorityMark priority={w.priority} />
              </td>
              <td>
                <Link to={`/work-orders/${w.id}`} className="data hover:underline">
                  {w.number}
                </Link>
              </td>
              <td>
                <TypeMark type={w.type} />
              </td>
              <td className="max-w-[22rem] truncate">
                <Link to={`/work-orders/${w.id}`} className="hover:underline" title={w.title}>
                  {w.title}
                </Link>
              </td>
              {!hideAsset && (
                <td>
                  <AssetTag id={w.asset.id} tag={w.asset.tag} />
                </td>
              )}
              {!compact && <td className="text-muted">{w.assignee?.name ?? <span className="stencil text-[12px]">Unassigned</span>}</td>}
              <td>
                <StatusLabel status={w.status} overdue={w.overdue} />
              </td>
              <td className="data text-right text-muted">{fmtDateTime(w.raisedAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </Scroll>
  );
}
