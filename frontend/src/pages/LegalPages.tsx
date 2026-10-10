import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { Logo } from '../components/icons';
import { Wordmark } from '../components/Layout';
import { useTitle } from '../components/toast';
import { FooterLinks, REPO_URL } from '../components/ui';

const UPDATED = '11 October 2026';

function LegalShell({ title, children }: { title: string; children: ReactNode }) {
  useTitle(title);
  return (
    <div className="min-h-screen">
      <div className="h-[2px] heat-bar" />
      <header className="max-w-3xl mx-auto px-5 pt-8 flex items-center justify-between">
        <Link to="/" className="flex items-center gap-2.5" aria-label="PlantDesk home">
          <Logo className="w-8 h-8" />
          <Wordmark className="text-[26px]" />
        </Link>
        <Link to="/" className="btn">Back to the app</Link>
      </header>
      <main className="max-w-3xl mx-auto px-5 py-12">
        <div className="stencil text-[11px] text-heat-4">Last updated {UPDATED}</div>
        <h1 className="font-display text-[48px] sm:text-[56px] leading-[0.95] text-white mt-3">{title}</h1>
        <div className="mt-8 space-y-8 text-[15px] leading-relaxed text-label/90 [&_h2]:font-display [&_h2]:text-[26px] [&_h2]:text-white [&_h2]:mb-2 [&_li]:ml-5 [&_li]:list-disc [&_li]:mt-1 [&_a]:text-tag [&_a]:underline [&_a]:underline-offset-4 [&_code]:font-mono [&_code]:text-[13px] [&_code]:text-tag">
          {children}
        </div>
      </main>
      <FooterLinks className="max-w-3xl mx-auto px-5 pb-10" />
    </div>
  );
}

export function PrivacyPage() {
  return (
    <LegalShell title="Privacy & cookies">
      <section>
        <h2>Who runs this</h2>
        <p>
          PlantDesk is an independent portfolio project, built and operated by the GitHub user{' '}
          <a href="https://github.com/ayushmanjha52" target="_blank" rel="noopener noreferrer">ayushmanjha52</a>. It is not a
          commercial service and nothing is sold here. Questions or requests go through the{' '}
          <a href={`${REPO_URL}/issues`} target="_blank" rel="noopener noreferrer">project's GitHub issues</a>.
        </p>
      </section>
      <section>
        <h2>What is stored</h2>
        <ul>
          <li>For each account: name, work email, role, trade, shift, and the password as a one-way BCrypt hash. Never the password itself.</li>
          <li>The maintenance records people enter: assets, work orders, labour time, spares used, meter readings.</li>
          <li>A hashed record of each sign-in session, so it can be revoked.</li>
        </ul>
        <p className="mt-3">
          Nothing else: no location, no device fingerprint, no tracking. The demo plants (<code>DEMO</code>, <code>LOCO</code>)
          contain fictional people only.
        </p>
      </section>
      <section>
        <h2>Cookies</h2>
        <p>
          PlantDesk sets exactly one cookie, <code>pd_refresh</code>. It keeps you signed in. It is strictly necessary,
          HttpOnly (page scripts cannot read it), Secure (sent over HTTPS only), SameSite=Strict, and it expires after 14 days
          or when you sign out. There are no analytics, advertising or third-party cookies. Because the only cookie is
          strictly necessary, no consent banner is shown.
        </p>
      </section>
      <section>
        <h2>Who else is involved</h2>
        <p>
          The app and its database run on <a href="https://railway.com" target="_blank" rel="noopener noreferrer">Railway</a>,
          which acts as the hosting provider. Fonts are served from this site, not from a font CDN, so your browser does not
          contact Google or anyone else when you use PlantDesk.
        </p>
      </section>
      <section>
        <h2>How long it is kept</h2>
        <p>
          The public demo plants are wiped and rebuilt every night, so anything typed there is gone within a day. Sign-in
          sessions expire after 14 days. Accounts in other plants stay until a plant admin deactivates them or deletion is
          requested.
        </p>
      </section>
      <section>
        <h2>Your rights and deletion</h2>
        <p>
          You can ask for a copy of your data, or for it to be corrected or deleted, by opening an issue on the{' '}
          <a href={`${REPO_URL}/issues`} target="_blank" rel="noopener noreferrer">GitHub repository</a>. Don't post personal
          details in the issue itself; say that you want to make a request and a private channel will be arranged.
        </p>
      </section>
      <section>
        <h2>Children</h2>
        <p>PlantDesk is a workplace tool for maintenance teams and is not meant for anyone under 18.</p>
      </section>
      <section>
        <h2>Security</h2>
        <p>
          All traffic is HTTPS. Every plant's data is isolated twice: in the application and by row-level security inside
          the database. Sign-in attempts are rate-limited. The source code is public on GitHub if you want to check any of
          this.
        </p>
      </section>
    </LegalShell>
  );
}

export function TermsPage() {
  return (
    <LegalShell title="Terms of use">
      <section>
        <h2>What this is</h2>
        <p>
          PlantDesk is a portfolio project that demonstrates a multi-tenant maintenance management system. It is free to try
          and there are no fees, subscriptions or payments of any kind, so there is nothing to refund.
        </p>
      </section>
      <section>
        <h2>Using the demo</h2>
        <ul>
          <li>Demo accounts are shared by everyone. Don't enter real personal, confidential or customer data.</li>
          <li>Demo data is reset every night and may be reset at any time.</li>
          <li>Don't try to disrupt the service, other users, or the hosting infrastructure.</li>
          <li>
            Found a security problem? Please report it privately by opening an issue that says you have one; don't publish
            the details.
          </li>
        </ul>
      </section>
      <section>
        <h2>No warranty</h2>
        <p>
          PlantDesk is provided as it is, without warranty of any kind. It is not certified for safety-critical use, and its
          figures (MTBF, MTTR, PM due dates) must not be the sole basis for decisions about equipment safety.
        </p>
      </section>
      <section>
        <h2>Source code</h2>
        <p>
          The code is published on <a href={REPO_URL} target="_blank" rel="noopener noreferrer">GitHub</a> for review and
          learning. Fonts used: Instrument Serif and Geist, both under the SIL Open Font License 1.1. The logo and
          illustrations are original to this project.
        </p>
      </section>
      <section>
        <h2>Changes</h2>
        <p>These terms may change as the project evolves. The date at the top shows the latest version.</p>
      </section>
    </LegalShell>
  );
}

export function NotFoundPage() {
  useTitle('Page not found');
  return (
    <div className="min-h-[60vh] grid place-items-center text-center px-4">
      <div>
        <div className="data text-[13px] text-heat-4">404</div>
        <h1 className="font-display text-[56px] leading-none text-white mt-2">
          Nothing on this <em className="heat-text italic">tag</em>.
        </h1>
        <p className="text-muted mt-4 max-w-md mx-auto">
          The page you asked for doesn't exist, or it belongs to a plant you're not signed in to.
        </p>
        <Link to="/" className="btn btn-go mt-6">Back to the board</Link>
      </div>
    </div>
  );
}
