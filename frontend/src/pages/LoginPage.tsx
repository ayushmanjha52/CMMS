import { useState, type FormEvent } from 'react';
import { login } from '../api/client';
import { IconBolt, IconCalendar, IconTrendDown, Logo } from '../components/icons';
import { ErrorPlate, Field } from '../components/ui';

const DEMO = [
  { role: 'Plant admin', email: 'admin@demo.plant', dot: 'from-heat-1 to-heat-2' },
  { role: 'Maintenance manager', email: 'manager@demo.plant', dot: 'from-heat-2 to-heat-3' },
  { role: 'Technician · electrical', email: 'electrical@demo.plant', dot: 'from-heat-3 to-heat-4' },
  { role: 'Viewer · plant head', email: 'viewer@demo.plant', dot: 'from-heat-4 to-heat-5' },
];

export function LoginPage() {
  const [plantCode, setPlantCode] = useState('DEMO');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(plantCode, email, password);
    } catch (err) {
      setError(err);
      setBusy(false);
    }
  }

  return (
    <div className="min-h-screen grid lg:grid-cols-[1.15fr_1fr]">
      {/* ---------- Hero ---------- */}
      <section className="relative hidden lg:flex flex-col justify-between p-12 overflow-hidden border-r border-engrave">
        <div className="absolute inset-0 -z-10 bg-[radial-gradient(800px_500px_at_20%_15%,rgba(123,47,247,0.45),transparent_60%),radial-gradient(700px_500px_at_85%_85%,rgba(255,61,127,0.35),transparent_60%),radial-gradient(500px_300px_at_70%_40%,rgba(255,138,61,0.18),transparent_70%)]" />
        <div className="flex items-center gap-3">
          <Logo className="w-11 h-11 drop-shadow-[0_0_18px_rgba(255,61,127,0.6)]" />
          <span className="stencil text-[26px] heat-text">PlantDesk</span>
        </div>

        <div className="max-w-xl">
          <div className="stencil text-[13px] text-heat-4 mb-3">Maintenance management for working plants</div>
          <h1 className="text-[46px] leading-[1.05] font-semibold tracking-tight text-white">
            See the machine that's{' '}
            <span className="heat-text heat-shimmer bg-[length:200%_100%]">about to fail</span> before it does.
          </h1>
          <p className="mt-5 text-[16px] text-muted max-w-lg">
            Breakdowns, preventive maintenance by calendar <em>or</em> running hours, spares, and MTBF trends, all in one
            place, for every plant on its own isolated data.
          </p>

          <HeroStrip />

          <ul className="mt-8 grid grid-cols-3 gap-3">
            {[
              { icon: IconTrendDown, title: 'MTBF trends', text: 'Degrading assets flagged automatically' },
              { icon: IconCalendar, title: 'Dual-trigger PM', text: 'Days or running hours, whichever first' },
              { icon: IconBolt, title: 'Shift-ready', text: 'Raise a breakdown in under a minute' },
            ].map(({ icon: Icon, title, text }) => (
              <li key={title} className="rounded-xl border border-white/10 bg-white/[0.04] p-3 backdrop-blur">
                <Icon className="w-5 h-5 text-heat-4" />
                <div className="stencil text-[14px] mt-2 text-white">{title}</div>
                <div className="text-[12.5px] text-muted leading-snug">{text}</div>
              </li>
            ))}
          </ul>
        </div>

        <div className="text-[12px] text-muted">Colour system borrowed from infrared thermography: cold violet to white-hot yellow.</div>
      </section>

      {/* ---------- Sign in ---------- */}
      <section className="flex items-center justify-center p-5 sm:p-10">
        <div className="w-full max-w-md">
          <div className="lg:hidden flex items-center gap-3 mb-6">
            <Logo className="w-10 h-10" />
            <span className="stencil text-[24px] heat-text">PlantDesk</span>
          </div>

          <div className="plate p-6 sm:p-7 relative">
            <div className="absolute inset-x-0 top-0 h-[3px] heat-bar rounded-t-xl" />
            <h2 className="stencil text-[24px] text-white">Sign in to your plant</h2>
            <p className="text-muted text-[13px] mt-1 mb-5">Use your plant code. Each plant's data is walled off from every other.</p>
            <form onSubmit={submit} className="space-y-4">
              <ErrorPlate error={error} />
              <Field label="Plant code">
                <input className="field data uppercase tracking-widest text-tag" value={plantCode} onChange={(e) => setPlantCode(e.target.value)} required autoComplete="organization" />
              </Field>
              <Field label="Email">
                <input className="field" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required autoComplete="username" placeholder="you@plant.com" />
              </Field>
              <Field label="Password">
                <input className="field" type="password" value={password} onChange={(e) => setPassword(e.target.value)} required autoComplete="current-password" />
              </Field>
              <button className="btn btn-go w-full justify-center h-11 text-[15px]" disabled={busy}>
                {busy ? 'Signing in…' : 'Sign in'}
              </button>
            </form>
          </div>

          <div className="mt-5">
            <div className="stencil text-[12px] text-muted mb-2 flex items-center justify-between">
              <span>Try the demo plant, one click</span>
              <span className="data normal-case tracking-normal text-[11.5px]">password plantdesk-demo</span>
            </div>
            <div className="grid sm:grid-cols-2 gap-2">
              {DEMO.map((d) => (
                <button
                  key={d.email}
                  type="button"
                  className="group text-left rounded-xl border border-engrave bg-white/[0.03] hover:border-heat-3/70 hover:bg-heat-3/10 px-3 py-2.5 transition"
                  onClick={() => {
                    setPlantCode('DEMO');
                    setEmail(d.email);
                    setPassword('plantdesk-demo');
                  }}
                >
                  <span className="flex items-center gap-2">
                    <span className={`w-2.5 h-2.5 rounded-full bg-gradient-to-br ${d.dot}`} />
                    <span className="text-[13px] text-white">{d.role}</span>
                  </span>
                  <span className="data text-muted text-[11.5px] block mt-0.5 group-hover:text-tag">{d.email}</span>
                </button>
              ))}
            </div>
            <p className="text-muted text-[12px] mt-3">
              A second plant, <span className="data text-tag">LOCO</span> (admin@loco.shed), shares the same database and cannot see a
              single DEMO record.
            </p>
          </div>
        </div>
      </section>
    </div>
  );
}

/** Decorative preview of the health strip: failures bunching as the MTBF line runs hot. */
function HeroStrip() {
  const ticks = [6, 22, 36, 48, 58, 66, 72, 77, 81, 84];
  const line = ticks.slice(1).map((t, i) => `${t * 4.4},${18 + i * 7.5 + (i > 5 ? (i - 5) * 4 : 0)}`).join(' ');
  return (
    <div className="mt-7 rounded-xl border border-white/10 bg-[#0b071b]/70 p-4 backdrop-blur">
      <div className="flex justify-between stencil text-[11px] text-muted mb-2">
        <span>Conveyor 1A drive motor · last 12 months</span>
        <span className="text-warning">MTBF falling</span>
      </div>
      <svg viewBox="0 0 440 100" className="w-full h-24">
        <defs>
          <linearGradient id="hero-hot" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#7b2ff7" />
            <stop offset="45%" stopColor="#ff3d7f" />
            <stop offset="75%" stopColor="#ff8a3d" />
            <stop offset="100%" stopColor="#ffd23f" />
          </linearGradient>
        </defs>
        {ticks.map((t, i) => (
          <line key={t} x1={t * 4.4} x2={t * 4.4} y1={8} y2={96} stroke={i > 6 ? '#ff4d6d' : '#ffa62b'} strokeWidth="3" strokeLinecap="round" opacity="0.9" />
        ))}
        <polyline points={line} fill="none" stroke="url(#hero-hot)" strokeWidth="3" strokeLinejoin="round" strokeLinecap="round" />
      </svg>
    </div>
  );
}
