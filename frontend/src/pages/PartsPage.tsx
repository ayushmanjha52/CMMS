import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api, post } from '../api/client';
import type { SparePart } from '../api/types';
import { useCan } from '../auth/AuthContext';
import { fmtMoney, fmtQty } from '../components/format';
import { Empty, ErrorPlate, Loading, PageTitle, Plate, Scroll } from '../components/ui';

export function PartsPage() {
  const can = useCan();
  const [lowOnly, setLowOnly] = useState(false);
  const list = useQuery({ queryKey: ['parts'], queryFn: () => api<SparePart[]>('/api/parts') });
  const rows = (list.data ?? []).filter((p) => !lowOnly || p.belowReorderPoint);

  return (
    <>
      <PageTitle
        right={
          <label className="stencil text-[12px] text-muted flex items-center gap-2">
            <input type="checkbox" checked={lowOnly} onChange={(e) => setLowOnly(e.target.checked)} />
            At or below reorder point
          </label>
        }
      >
        Spares
      </PageTitle>
      <ErrorPlate error={list.error} />
      <Plate title={`${rows.length} stock items`}>
        {list.isLoading ? <Loading /> : rows.length === 0 ? <Empty>No stock items in this view.</Empty> : (
          <Scroll>
            <table className="grid-table">
              <thead>
                <tr>
                  <th>Part no.</th>
                  <th>Description</th>
                  <th>Bin</th>
                  <th className="text-right">In stock</th>
                  <th className="text-right">Reorder at</th>
                  <th className="text-right">Unit cost</th>
                  {can.manageWork && <th>Goods receipt</th>}
                </tr>
              </thead>
              <tbody>
                {rows.map((p) => (
                  <tr key={p.id}>
                    <td className="data">{p.partNumber}</td>
                    <td className="max-w-[22rem] truncate">{p.description}</td>
                    <td className="data text-muted">{p.binLocation ?? '—'}</td>
                    <td className={`data text-right ${p.belowReorderPoint ? 'text-warning' : ''}`}>
                      {fmtQty(p.stockQty)} <span className="text-muted">{p.unit}</span>
                    </td>
                    <td className="data text-right text-muted">{fmtQty(p.reorderPoint)}</td>
                    <td className="data text-right">{fmtMoney(p.unitCost)}</td>
                    {can.manageWork && <td><Receipt part={p} /></td>}
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

function Receipt({ part }: { part: SparePart }) {
  const qc = useQueryClient();
  const [qty, setQty] = useState('');
  const m = useMutation({
    mutationFn: () => post<SparePart>(`/api/parts/${part.id}/receipts`, { quantity: qty }),
    onSuccess: () => {
      setQty('');
      qc.invalidateQueries({ queryKey: ['parts'] });
      qc.invalidateQueries({ queryKey: ['summary'] });
    },
  });
  return (
    <form
      className="flex gap-1"
      onSubmit={(e) => {
        e.preventDefault();
        m.mutate();
      }}
      title={m.error instanceof Error ? m.error.message : undefined}
    >
      <input
        className={`field data h-6 w-20 ${m.error ? 'border-warning' : ''}`}
        inputMode="decimal"
        placeholder="qty"
        value={qty}
        onChange={(e) => setQty(e.target.value.replace(/[^\d.]/g, ''))}
        aria-label={`Quantity received of ${part.partNumber}`}
      />
      <button className="btn h-6 px-2" disabled={!qty || m.isPending}>
        Receive
      </button>
    </form>
  );
}
