import { useState, type FormEvent } from 'react';
import { login } from '../api/client';
import { IconCalendar, IconTrendDown, IconTree, Logo } from '../components/icons';
import { Wordmark } from '../components/Layout';
import { useTitle } from '../components/toast';
import { ErrorPlate, Field, FooterLinks } from '../components/ui';

const DEMO = [
  { role: 'Maintenance manager', email: 'manager@demo.plant', hint: 'Board, watch list, approvals', dot: 'from-heat-2 to-heat-3' },
  { role: 'Technician', email: 'electrical@demo.plant', hint: 'Only his own jobs', dot: 'from-heat-3 to-heat-4' },
  { role: 'Plant admin', email: 'admin@demo.plant', hint: 'Assets and users', dot: 'from-heat-1 to-heat-2' },
  { role: 'Viewer', email: 'viewer@demo.plant', hint: 'Read-only dashboards', dot: 'from-heat-4 to-heat-5' },
];

export function LoginPage() {
  useTitle('');
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
    <div className="min-h-screen grid lg:grid-cols-[1.2fr_1fr]">
      {/* ---------- Hero ---------- */}
      <section className="relative hidden lg:flex flex-col justify-between p-14 overflow-hidden border-r border-engrave">
        <div className="absolute inset-0 -z-10 bg-[radial-gradient(700px_460px_at_12%_8%,rgba(123,47,247,0.30),transparent_60%),radial-gradient(640px_480px_at_88%_92%,rgba(255,61,127,0.26),transparent_60%),radial-gradient(420px_260px_at_70%_42%,rgba(255,138,61,0.10),transparent_70%)]" />
        <div className="flex items-center gap-3">
          <Logo className="w-11 h-11 drop-shadow-[0_0_18px_rgba(255,61,127,0.55)]" />
          <Wordmark className="text-[34px]" />
        </div>

        <div className="max-w-2xl">
          <div className="stencil text-[11px] text-heat-4 mb-5">Maintenance management for working plants</div>
          <h1 className="font-display text-[68px] xl:text-[76px] leading-[0.92] text-white">
            Spot the machine that's <em className="heat-text heat-shimmer italic">wearing out</em> before it stops your plant.
          </h1>
          <p className="mt-6 text-[16.5px] text-muted max-w-lg leading-relaxed">
            Breakdowns, preventive maintenance by calendar or running hours, spares and MTBF trends, in one place. Every plant
            on its own walled-off data.
          </p>

          <HeroStrip />

          <ul className="mt-8 grid grid-cols-3 gap-3">
            {[
              { icon: IconTrendDown, title: 'MTBF trends', text: 'Shrinking gaps between failures are flagged' },
              { icon: IconCalendar, title: 'Dual-trigger PM', text: 'Days or running hours, whichever comes first' },
              { icon: IconTree, title: 'Asset tags', text: 'Plant → area → line → machine → component' },
            ].map(({ icon: Icon, title, text }) => (
              <li key={title} className="rounded-xl border border-white/[0.08] bg-white/[0.025] p-4 backdrop-blur">
                <Icon className="w-5 h-5 text-heat-4" />
                <div className="text-[14.5px] font-semibold mt-2.5 text-white">{title}</div>
                <div className="text-[13px] text-muted leading-snug mt-0.5">{text}</div>
              </li>
            ))}
          </ul>
        </div>

        <FooterLinks />
      </section>

      {/* ---------- Sign in ---------- */}
      <section className="flex flex-col justify-center p-5 sm:p-10">
        <div className="w-full max-w-md mx-auto">
          <div className="lg:hidden flex items-center gap-3 mb-8">
            <Logo className="w-10 h-10" />
            <Wordmark className="text-[30px]" />
          </div>

          <h2 className="font-display text-[42px] leading-none text-white">Welcome back</h2>
          <p className="text-muted text-[14px] mt-2 mb-7">Sign in with your plant code. Each plant's data is walled off from every other.</p>

          <form onSubmit={submit} className="space-y-4" noValidate={false}>
            <ErrorPlate error={error} />
            <Field label="Plant code">
              <input className="field data uppercase tracking-[0.2em] text-tag" value={plantCode} onChange={(e) => setPlantCode(e.target.value)} required maxLength={32} autoComplete="organization" />
            </Field>
            <Field label="Email">
              <input className="field" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required maxLength={254} autoComplete="username" placeholder="you@yourplant.com" />
            </Field>
            <Field label="Password">
              <input className="field" type="password" value={password} onChange={(e) => setPassword(e.target.value)} required maxLength={72} autoComplete="current-password" />
            </Field>
            <button className="btn btn-go w-full justify-center h-11 text-[15px] rounded-xl" disabled={busy}>
              {busy ? 'Signing in…' : 'Sign in'}
            </button>
          </form>

          <div className="mt-9">
            <div className="flex items-baseline justify-between mb-3">
              <span className="text-[13.5px] font-medium text-white">Try the demo plant</span>
              <span className="text-[12px] text-muted">
                password <span className="data text-tag">plantdesk-demo</span>
              </span>
            </div>
            <div className="grid sm:grid-cols-2 gap-2">
              {DEMO.map((d) => (
                <button
                  key={d.email}
                  type="button"
                  className="group text-left rounded-xl border border-engrave bg-white/[0.02] hover:border-heat-3/60 hover:bg-heat-3/[0.06] px-3.5 py-3 transition"
                  onClick={() => {
                    setPlantCode('DEMO');
                    setEmail(d.email);
                    setPassword('plantdesk-demo');
                  }}
                >
                  <span className="flex items-center gap-2">
                    <span className={`w-2.5 h-2.5 rounded-full bg-gradient-to-br ${d.dot}`} />
                    <span className="text-[13.5px] font-medium text-white">{d.role}</span>
                  </span>
                  <span className="text-[12px] text-muted block mt-1">{d.hint}</span>
                </button>
              ))}
            </div>
            <p className="text-muted text-[12.5px] mt-4 leading-relaxed">
              Click a role to fill the form, then sign in. A second plant, <span className="data text-tag">LOCO</span>{' '}
              (<span className="data">admin@loco.shed</span>), shares the same database and can't see a single DEMO record.
              Demo data resets every night.
            </p>
          </div>
          <FooterLinks className="lg:hidden mt-10" />
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
    <div className="mt-8 rounded-2xl border border-white/[0.08] bg-black/40 p-5 backdrop-blur" aria-hidden="true">
      <div className="flex justify-between text-[12px] text-muted mb-2">
        <span>Conveyor 1A drive motor · last 12 months</span>
        <span className="text-warning font-medium">MTBF falling</span>
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
