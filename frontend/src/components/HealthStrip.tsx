import { useNavigate } from 'react-router-dom';
import type { HealthStrip as Strip, Priority } from '../api/types';
import { fmtDate, fmtHours, fmtMinutes } from './format';

const W = 1000;
const H = 210;
const M = { left: 56, right: 16, top: 16, bottom: 58 };
const PLOT_W = W - M.left - M.right;
const PLOT_H = H - M.top - M.bottom;
const HEAT_ROW_Y = M.top + PLOT_H + 10;
const HEAT_ROW_H = 14;

const TICK_COLOR: Record<Priority, string> = {
  EMERGENCY: 'var(--danger)',
  HIGH: 'var(--warning)',
  MEDIUM: 'var(--caution)',
  LOW: 'var(--label-muted)',
};

// Ironbow, cold → hot, for the monthly failure-density row.
const IRONBOW = ['#1d1147', '#3b1c9e', '#7b2ff7', '#c026d3', '#ff3d7f', '#ff8a3d', '#ffd23f'];
const MONTHS = ['JAN', 'FEB', 'MAR', 'APR', 'MAY', 'JUN', 'JUL', 'AUG', 'SEP', 'OCT', 'NOV', 'DEC'];

/**
 * Twelve months, one vertical tick per failure, rolling MTBF drawn through them, a thermal
 * row of failure density underneath. The MTBF line is coloured by its own height: it runs
 * cold violet while failures are far apart and turns white-hot as the gap closes — so a
 * degrading machine literally heats up on screen.
 */
export function HealthStrip({ strip }: { strip: Strip }) {
  const navigate = useNavigate();
  const from = new Date(strip.from).getTime();
  const to = new Date(strip.to).getTime();
  const xAt = (ms: number) => M.left + ((ms - from) / (to - from)) * PLOT_W;
  const x = (iso: string) => xAt(new Date(iso).getTime());

  const maxMtbf = niceCeil(Math.max(0, ...strip.trend.points.map((p) => p.rollingMtbfHours)));
  const y = (h: number) => M.top + PLOT_H - (maxMtbf === 0 ? 0 : (h / maxMtbf) * PLOT_H);

  // Month boundaries, and failures counted per month for the heat row.
  const months: { start: number; end: number; label: string; count: number }[] = [];
  const cursor = new Date(from);
  cursor.setUTCDate(1);
  cursor.setUTCHours(0, 0, 0, 0);
  while (cursor.getTime() < to) {
    const start = Math.max(cursor.getTime(), from);
    const label = MONTHS[cursor.getUTCMonth()];
    cursor.setUTCMonth(cursor.getUTCMonth() + 1);
    const end = Math.min(cursor.getTime(), to);
    const count = strip.failures.filter((f) => {
      const t = new Date(f.at).getTime();
      return t >= start && t < end;
    }).length;
    months.push({ start, end, label, count });
  }
  const maxCount = Math.max(1, ...months.map((m) => m.count));

  const pts = strip.trend.points.map((p) => [x(p.at), y(p.rollingMtbfHours)] as const);
  const line = pts.map(([px, py]) => `${px.toFixed(1)},${py.toFixed(1)}`).join(' ');
  const area = pts.length > 1 ? `M${pts[0][0]},${M.top + PLOT_H} L${line.replace(/ /g, ' L')} L${pts[pts.length - 1][0]},${M.top + PLOT_H} Z` : '';
  const t = strip.trend;

  return (
    <div>
      <div className="flex flex-wrap items-center gap-x-6 gap-y-2 px-4 py-3 border-b border-engrave/70">
        <Verdict trend={t.trend} older={t.olderMeanGapHours} recent={t.recentMeanGapHours} />
        <Legend />
      </div>
      <div className="px-2 pt-2 bg-[radial-gradient(120%_100%_at_100%_0%,rgba(255,61,127,0.08),transparent_60%)]">
        <svg
          viewBox={`0 0 ${W} ${H}`}
          className="w-full h-auto block"
          role="img"
          aria-label={`${strip.failures.length} failures in 12 months. Trend: ${t.trend.toLowerCase().replace('_', ' ')}.`}
        >
          <defs>
            {/* Height-keyed heat: top of the plot (long MTBF) cold, bottom (short MTBF) hot. */}
            <linearGradient id="mtbf-heat" gradientUnits="userSpaceOnUse" x1="0" y1={M.top} x2="0" y2={M.top + PLOT_H}>
              <stop offset="0%" stopColor="#8b5cf6" />
              <stop offset="40%" stopColor="#c026d3" />
              <stop offset="65%" stopColor="#ff3d7f" />
              <stop offset="85%" stopColor="#ff8a3d" />
              <stop offset="100%" stopColor="#ffd23f" />
            </linearGradient>
            <linearGradient id="mtbf-area" gradientUnits="userSpaceOnUse" x1="0" y1={M.top} x2="0" y2={M.top + PLOT_H}>
              <stop offset="0%" stopColor="#7b2ff7" stopOpacity="0.28" />
              <stop offset="100%" stopColor="#ff3d7f" stopOpacity="0.02" />
            </linearGradient>
            {/* userSpaceOnUse: a bounding-box filter on a perfectly vertical line has zero width and erases it. */}
            <filter id="glow" filterUnits="userSpaceOnUse" x="0" y="0" width={W} height={H}>
              <feGaussianBlur stdDeviation="3" result="b" />
              <feMerge>
                <feMergeNode in="b" />
                <feMergeNode in="SourceGraphic" />
              </feMerge>
            </filter>
          </defs>

          <rect x={M.left} y={M.top} width={PLOT_W} height={PLOT_H} rx="6" fill="rgba(11,7,27,0.55)" stroke="var(--engrave)" />
          {/* Horizontal guides at quarters of the MTBF scale. */}
          {maxMtbf > 0 &&
            [0.25, 0.5, 0.75].map((f) => (
              <line key={f} x1={M.left} x2={M.left + PLOT_W} y1={y(maxMtbf * f)} y2={y(maxMtbf * f)} stroke="var(--engrave)" strokeDasharray="2 4" />
            ))}
          {months.slice(1).map((m) => (
            <line key={m.start} x1={xAt(m.start)} x2={xAt(m.start)} y1={M.top} y2={M.top + PLOT_H} stroke="rgba(74,60,140,0.35)" />
          ))}

          {maxMtbf > 0 && (
            <>
              <text x={M.left - 8} y={M.top + 10} textAnchor="end" fill="var(--label-muted)" fontSize="11" fontFamily="JetBrains Mono">
                {maxMtbf}h
              </text>
              <text x={M.left - 8} y={y(maxMtbf / 2) + 4} textAnchor="end" fill="var(--label-muted)" fontSize="11" fontFamily="JetBrains Mono">
                {maxMtbf / 2}h
              </text>
            </>
          )}

          {area && <path d={area} fill="url(#mtbf-area)" />}

          {/* Failure ticks: colour = priority, glowing. Click opens the work order. */}
          {strip.failures.map((f) => {
            const fx = x(f.at);
            return (
              <g key={f.workOrderId} className="cursor-pointer" onClick={() => navigate(`/work-orders/${f.workOrderId}`)}>
                <title>
                  {`${f.number} · ${fmtDate(f.at)}\n${f.failureCode ?? 'uncoded'} ${f.failureDescription ?? ''}\nDowntime ${fmtMinutes(f.downtimeMinutes)}`}
                </title>
                <rect x={fx - 7} y={M.top} width={14} height={PLOT_H} fill="transparent" />
                <line x1={fx} x2={fx} y1={M.top + 6} y2={M.top + PLOT_H - 2} stroke={TICK_COLOR[f.priority]} strokeWidth={3.5} strokeLinecap="round" filter="url(#glow)" />
              </g>
            );
          })}

          {pts.length > 1 && (
            <polyline points={line} fill="none" stroke="url(#mtbf-heat)" strokeWidth={3.5} strokeLinejoin="round" strokeLinecap="round" filter="url(#glow)" />
          )}
          {strip.trend.points.map((p) => (
            <circle key={p.at} cx={x(p.at)} cy={y(p.rollingMtbfHours)} r={4.5} fill="#0f0a24" stroke="url(#mtbf-heat)" strokeWidth={2.5}>
              <title>{`Rolling MTBF ${fmtHours(p.rollingMtbfHours)} at ${fmtDate(p.at)}`}</title>
            </circle>
          ))}

          {/* Thermal row: failures per month on the ironbow scale. */}
          {months.map((m) => {
            const idx = m.count === 0 ? 0 : Math.max(2, Math.round((m.count / maxCount) * (IRONBOW.length - 1)));
            return (
              <g key={`h${m.start}`}>
                <rect x={xAt(m.start) + 1} y={HEAT_ROW_Y} width={Math.max(0, xAt(m.end) - xAt(m.start) - 2)} height={HEAT_ROW_H} rx="3" fill={IRONBOW[idx]}>
                  <title>{`${m.label}: ${m.count} failure${m.count === 1 ? '' : 's'}`}</title>
                </rect>
                <text x={(xAt(m.start) + xAt(m.end)) / 2} y={H - 12} textAnchor="middle" fill="var(--label-muted)" fontSize="11.5" fontFamily="Barlow Condensed" letterSpacing="1.2">
                  {m.label}
                </text>
              </g>
            );
          })}
          <text x={M.left - 8} y={HEAT_ROW_Y + 11} textAnchor="end" fill="var(--label-muted)" fontSize="10" fontFamily="Barlow Condensed" letterSpacing="1">
            HEAT
          </text>
        </svg>
      </div>
      {strip.failures.length === 0 && (
        <div className="px-4 pb-4 text-muted text-[13px]">No failures recorded on this asset in the last 12 months. Running cold, as it should.</div>
      )}
    </div>
  );
}

function Verdict({ trend, older, recent }: { trend: Strip['trend']['trend']; older: number | null; recent: number | null }) {
  if (trend === 'DEGRADING') {
    return (
      <span className="flex items-center gap-3">
        <span className="chip text-[#2a1600] bg-warning border-transparent text-[12px] h-6 shadow-[0_0_18px_-4px_rgba(255,166,43,0.9)]">MTBF falling</span>
        <span className="data text-label">
          {fmtHours(recent)} between failures <span className="text-muted">· was {fmtHours(older)}</span>
        </span>
      </span>
    );
  }
  if (trend === 'IMPROVING') {
    return (
      <span className="flex items-center gap-3">
        <span className="chip text-safe border-safe/50 bg-safe/10 text-[12px] h-6">MTBF rising</span>
        <span className="data text-label">
          {fmtHours(recent)} <span className="text-muted">· was {fmtHours(older)}</span>
        </span>
      </span>
    );
  }
  if (trend === 'STABLE') return <span className="chip text-info border-info/50 bg-info/10 text-[12px] h-6">MTBF steady</span>;
  return <span className="stencil text-[13px] text-muted">Not enough failures to call a trend</span>;
}

function Legend() {
  const tick = (color: string, label: string) => (
    <span className="inline-flex items-center gap-1.5">
      <span className="inline-block w-[3px] h-3.5 rounded-full" style={{ background: color, boxShadow: `0 0 6px ${color}` }} />
      {label}
    </span>
  );
  return (
    <span className="stencil text-[11px] text-muted flex flex-wrap items-center gap-4 ml-auto">
      {tick('var(--danger)', 'Emergency')}
      {tick('var(--warning)', 'High')}
      {tick('var(--caution)', 'Medium')}
      <span className="inline-flex items-center gap-1.5">
        <span className="inline-block w-6 h-[3px] rounded-full" style={{ background: 'linear-gradient(90deg,#8b5cf6,#ff3d7f,#ffd23f)' }} />
        Rolling MTBF · hotter = shorter
      </span>
    </span>
  );
}

function niceCeil(v: number): number {
  if (v <= 0) return 0;
  const step = 10 ** Math.floor(Math.log10(v));
  return Math.ceil(v / step) * step;
}
