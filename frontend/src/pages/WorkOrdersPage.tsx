import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import type { Page, WorkOrderListItem } from '../api/types';
import { useCan } from '../auth/AuthContext';
import { WorkOrderTable } from '../components/WorkOrderTable';
import { Empty, ErrorPlate, Loading, PageTitle, Plate } from '../components/ui';

const VIEWS = {
  outstanding: { label: 'Outstanding', query: 'status=OPEN&status=IN_PROGRESS&status=ON_HOLD' },
  review: { label: 'Awaiting closure', query: 'status=COMPLETED' },
  closed: { label: 'Closed', query: 'status=CLOSED&status=CANCELLED' },
  all: { label: 'All', query: '' },
} as const;

type View = keyof typeof VIEWS;

export function WorkOrdersPage() {
  const can = useCan();
  const [view, setView] = useState<View>('outstanding');
  const [type, setType] = useState<'' | 'BREAKDOWN' | 'PREVENTIVE'>('');
  const [page, setPage] = useState(0);

  const params = [VIEWS[view].query, type && `type=${type}`, `page=${page}`, 'size=50'].filter(Boolean).join('&');
  const list = useQuery({
    queryKey: ['work-orders', params],
    queryFn: () => api<Page<WorkOrderListItem>>(`/api/work-orders?${params}`),
    placeholderData: (prev) => prev,
  });

  return (
    <>
      <PageTitle
        right={
          can.doWork && (
            <Link to="/work-orders/raise" className="btn btn-go">
              {can.isTechnician ? 'Raise breakdown' : 'Raise work order'}
            </Link>
          )
        }
      >
        {can.isTechnician ? 'My work orders' : 'Work orders'}
      </PageTitle>

      <div className="flex flex-wrap gap-2 mb-3">
        <div className="inline-flex border border-engrave rounded overflow-hidden">
          {(Object.keys(VIEWS) as View[]).map((v) => (
            <button
              key={v}
              onClick={() => {
                setView(v);
                setPage(0);
              }}
              className={`stencil text-[12px] px-3 h-8 border-r border-engrave last:border-r-0 ${view === v ? 'bg-label text-surface' : 'text-muted hover:text-label'}`}
            >
              {VIEWS[v].label}
            </button>
          ))}
        </div>
        <select className="field w-auto stencil text-[12px]" value={type} onChange={(e) => setType(e.target.value as typeof type)}>
          <option value="">All types</option>
          <option value="BREAKDOWN">Breakdown</option>
          <option value="PREVENTIVE">Preventive</option>
        </select>
      </div>

      <ErrorPlate error={list.error} />
      <Plate
        title={list.data ? `${list.data.totalElements} work orders` : 'Work orders'}
        right={
          list.data && list.data.totalPages > 1 && (
            <span className="flex items-center gap-2">
              <button className="btn h-6" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
                Prev
              </button>
              <span className="data">
                {page + 1}/{list.data.totalPages}
              </span>
              <button className="btn h-6" disabled={page + 1 >= list.data.totalPages} onClick={() => setPage((p) => p + 1)}>
                Next
              </button>
            </span>
          )
        }
      >
        {list.isLoading ? (
          <Loading />
        ) : list.data && list.data.content.length > 0 ? (
          <WorkOrderTable rows={list.data.content} />
        ) : (
          <Empty>
            {can.isTechnician
              ? 'Nothing assigned to you in this view. Jobs your supervisor assigns, and breakdowns you raise, appear here.'
              : 'No work orders in this view. Breakdowns raised here will appear in this list.'}
          </Empty>
        )}
      </Plate>
    </>
  );
}
