import { useState, type FormEvent } from 'react';
import { login } from '../api/client';
import { ErrorPlate, Field } from '../components/ui';

const DEMO = [
  ['Plant admin', 'admin@demo.plant'],
  ['Maintenance manager', 'manager@demo.plant'],
  ['Technician (electrical)', 'electrical@demo.plant'],
  ['Viewer', 'viewer@demo.plant'],
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
    <div className="min-h-full grid place-items-center p-4">
      <div className="w-full max-w-sm">
        <div className="plate">
          <div className="px-4 pt-4 pb-3 border-b border-engrave">
            <div className="stencil text-[28px] leading-none">PlantDesk</div>
            <div className="stencil text-[12px] text-muted mt-1">Maintenance management</div>
          </div>
          <form onSubmit={submit} className="p-4 space-y-3">
            <ErrorPlate error={error} />
            <Field label="Plant code">
              <input className="field data uppercase" value={plantCode} onChange={(e) => setPlantCode(e.target.value)} required autoComplete="organization" />
            </Field>
            <Field label="Email">
              <input className="field" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required autoComplete="username" />
            </Field>
            <Field label="Password">
              <input className="field" type="password" value={password} onChange={(e) => setPassword(e.target.value)} required autoComplete="current-password" />
            </Field>
            <button className="btn btn-go w-full justify-center" disabled={busy}>
              {busy ? 'Signing in…' : 'Sign in'}
            </button>
          </form>
        </div>

        <div className="plate mt-3">
          <div className="plate-head">Demo plant · password plantdesk-demo</div>
          <ul>
            {DEMO.map(([role, mail]) => (
              <li key={mail}>
                <button
                  type="button"
                  className="w-full flex justify-between items-center px-3 h-8 border-b border-engrave last:border-b-0 hover:bg-surface text-left"
                  onClick={() => {
                    setPlantCode('DEMO');
                    setEmail(mail);
                    setPassword('plantdesk-demo');
                  }}
                >
                  <span className="text-[13px]">{role}</span>
                  <span className="data text-muted">{mail}</span>
                </button>
              </li>
            ))}
          </ul>
          <div className="px-3 py-2 text-muted text-[12px] border-t border-engrave">
            A second tenant, plant code <span className="data text-label">LOCO</span> (admin@loco.shed), shares the database and
            sees none of DEMO's data.
          </div>
        </div>
      </div>
    </div>
  );
}
