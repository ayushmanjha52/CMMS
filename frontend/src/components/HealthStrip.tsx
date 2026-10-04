import { useNavigate } from 'react-router-dom';
import type { HealthStrip as Strip, Priority } from '../api/types';
import { fmtDate, fmtHours, fmtMinutes } from './format';

const W = 1000;
const H = 168;
const M = { left: 52, right: 14, top: 14, bottom: 26 };
const PLOT_W = W - M.left - M.right;
const PLOT_H = H - M.top - M.bottom;

const TICK_COLOR: Record<Priority, string> = {
  EMERGENCY: 'var(--danger)',
  HIGH: 'var(--warning)',
  MEDIUM: 'var(--caution)',
  LOW: 'var(--label-muted)',
};

const MONTHS = ['JAN', 'FEB', 'MAR', 'APR', 'MAY', 'JUN', 'JUL', 'AUG', 'SEP', 'OCT', 'NOV', 'DEC'];

/**
 * Twelve months, one vertical tick per failure, rolling MTBF drawn through them.
 * Degrading equipment shows as ticks bunching toward the right while the line falls —
 * readable in one glance, before any number is read.
 */
export function HealthStrip({ strip }: { strip: Strip }) {
  const navigate = useNavigate();
  const from = new Date(strip.from).getTime();
  const to = new Date(strip.to).getTime();
  const x = (iso: string) => M.left + ((new Date(iso).getTime() - from) / (to - from)) * PLOT_W;

  const maxMtbf = niceCeil(Math.max(0, ...strip.trend.points.map((p) => p.rollingMtbfHours)));
  const y = (h: number) => M.top + PLOT_H - (maxMtbf === 0 ? 0 : (h / maxMtbf) * PLOT_H);

  const months: { at: number; label: string }[] = [];
  const cursor = new Date(from);
  cursor.setUTCDate(1);
  cursor.setUTCHours(0, 0, 0, 0);
  cursor.setUTCMonth(cursor.getUTCMonth() + 1);
  while (cursor.getTime() < to) {
    months.push({ at: cursor.getTime(), label: MONTHS[cursor.getUTCMonth()] });
    cursor.setUTCMonth(cursor.getUTCMonth() + 1);
  }

  const line = strip.trend.points.map((p) => `${x(p.at).toFixed(1)},${y(p.rollingMtbfHours).toFixed(1)}`).join(' ');
  const t = strip.trend;

  return (
    <div>
      <div className="flex flex-wrap items-baseline gap-x-6 gap-y-1 px-3 py-2 border-b border-engrave">
        <Verdict trend={t.trend} older={t.olderMeanGapHours} recent={t.recentMeanGapHours} />
        <Legend />
      </div>
      <svg
        viewBox={`0 0 ${W} ${H}`}
        className="w-full h-auto block"
        role="img"
        aria-label={`${strip.failures.length} failures in 12 months. Trend: ${t.trend.toLowerCase().replace('_', ' ')}.`}
      >
        {/* Plot frame and month rules: etched hairlines. */}
        <rect x={M.left} y={M.top} width={PLOT_W} height={PLOT_H} fill="none" stroke="var(--engrave)" />
        {months.map((m) => {
          const mx = M.left + ((m.at - from) / (to - from)) * PLOT_W;
          return (
            <g key={m.at}>
              <line x1={mx} x2={mx} y1={M.top} y2={M.top + PLOT_H} stroke="var(--engrave)" strokeDasharray="2 3" />
              <text x={mx + 3} y={H - 9} fill="var(--label-muted)" fontSize="11" fontFamily="Barlow Condensed" letterSpacing="1">
                {m.label}
              </text>
            </g>
          );
        })}

        {/* MTBF scale (left). */}
        {maxMtbf > 0 && (
          <>
            <text x={M.left - 6} y={M.top + 9} textAnchor="end" fill="var(--label-muted)" fontSize="10.5" fontFamily="JetBrains Mono">
              {maxMtbf}h
            </text>
            <text x={M.left - 6} y={M.top + PLOT_H} textAnchor="end" fill="var(--label-muted)" fontSize="10.5" fontFamily="JetBrains Mono">
              0
            </text>
          </>
        )}

        {/* Failure ticks: colour = priority. Clicking one opens its work order. */}
        {strip.failures.map((f) => {
          const fx = x(f.at);
          return (
            <g key={f.workOrderId} className="cursor-pointer" onClick={() => navigate(`/work-orders/${f.workOrderId}`)}>
              <title>
                {`${f.number} · ${fmtDate(f.at)}\n${f.failureCode ?? 'uncoded'} ${f.failureDescription ?? ''}\nDowntime ${fmtMinutes(f.downtimeMinutes)}`}
              </title>
              <rect x={fx - 6} y={M.top} width={12} height={PLOT_H} fill="transparent" />
              <line x1={fx} x2={fx} y1={M.top + 4} y2={M.top + PLOT_H} stroke={TICK_COLOR[f.priority]} strokeWidth={3} />
            </g>
          );
        })}

        {/* Rolling MTBF line over the ticks. */}
        {strip.trend.points.length > 1 && (
          <polyline points={line} fill="none" stroke="var(--label)" strokeWidth={1.75} strokeLinejoin="round" />
        )}
        {strip.trend.points.map((p) => (
          <rect key={p.at} x={x(p.at) - 2.5} y={y(p.rollingMtbfHours) - 2.5} width={5} height={5} fill="var(--label)">
            <title>{`Rolling MTBF ${fmtHours(p.rollingMtbfHours)} at ${fmtDate(p.at)}`}</title>
          </rect>
        ))}
      </svg>
      {strip.failures.length === 0 && (
        <div className="px-3 pb-3 text-muted text-[13px]">No failures recorded on this asset in the last 12 months.</div>
      )}
    </div>
  );
}

function Verdict({ trend, older, recent }: { trend: Strip['trend']['trend']; older: number | null; recent: number | null }) {
  if (trend === 'DEGRADING') {
    return (
      <span className="stencil text-[14px] text-warning">
        MTBF falling <span className="data normal-case tracking-normal text-[12px]">· {fmtHours(recent)} between failures, was {fmtHours(older)}</span>
      </span>
    );
  }
  if (trend === 'IMPROVING') {
    return (
      <span className="stencil text-[14px] text-safe">
        MTBF rising <span className="data normal-case tracking-normal text-[12px]">· {fmtHours(recent)}, was {fmtHours(older)}</span>
      </span>
    );
  }
  if (trend === 'STABLE') return <span className="stencil text-[14px] text-label">MTBF steady</span>;
  return <span className="stencil text-[14px] text-muted">Not enough failures to call a trend</span>;
}

function Legend() {
  const item = (color: string, label: string) => (
    <span className="inline-flex items-center gap-1.5">
      <span className="inline-block w-[3px] h-3" style={{ background: color }} />
      {label}
    </span>
  );
  return (
    <span className="stencil text-[11px] text-muted flex flex-wrap gap-3 ml-auto">
      {item('var(--danger)', 'Emergency')}
      {item('var(--warning)', 'High')}
      {item('var(--caution)', 'Medium')}
      <span className="inline-flex items-center gap-1.5">
        <span className="inline-block w-4 h-[2px] bg-label" />
        Rolling MTBF (last 3 gaps)
      </span>
    </span>
  );
}

function niceCeil(v: number): number {
  if (v <= 0) return 0;
  const step = 10 ** Math.floor(Math.log10(v));
  return Math.ceil(v / step) * step;
}
