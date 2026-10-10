import { useEffect, useState, type ReactNode } from 'react';
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { logout } from '../api/client';
import { useAuth, useCan } from '../auth/AuthContext';
import { IconBoard, IconBox, IconCalendar, IconTree, IconWrench, Logo } from './icons';
import { FooterLinks } from './ui';

const ROLE_LABEL = {
  PLANT_ADMIN: 'Plant admin',
  MAINTENANCE_MANAGER: 'Maintenance manager',
  TECHNICIAN: 'Technician',
  VIEWER: 'Viewer',
} as const;

export function Wordmark({ className = '' }: { className?: string }) {
  return (
    <span className={`font-display leading-none text-white ${className}`}>
      Plant<em className="heat-text font-display italic pr-[0.06em]">Desk</em>
    </span>
  );
}

export function Layout({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const can = useCan();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const location = useLocation();
  const [menuOpen, setMenuOpen] = useState(false);

  // Close the mobile menu whenever the route changes.
  useEffect(() => setMenuOpen(false), [location.pathname]);

  const nav = [
    can.seeAnalytics && { to: '/', label: 'Board', end: true, icon: IconBoard },
    { to: '/work-orders', label: can.isTechnician ? 'My work orders' : 'Work orders', icon: IconWrench },
    { to: '/assets', label: 'Assets', icon: IconTree },
    can.seeAnalytics && { to: '/pm', label: 'PM schedules', icon: IconCalendar },
    { to: '/spares', label: 'Spares', icon: IconBox },
  ].filter(Boolean) as { to: string; label: string; end?: boolean; icon: typeof IconBoard }[];

  const initials = (user?.fullName ?? '?').split(' ').map((p) => p[0]).slice(0, 2).join('');

  async function signOut() {
    // Back to the root first, so the next person at a shared shift terminal does not land
    // on the previous user's page.
    navigate('/', { replace: true });
    await logout();
    queryClient.clear();
  }

  const links = (
    <nav aria-label="Main" className="flex flex-col gap-1 px-3 py-3">
      {nav.map(({ to, label, end, icon: Icon }) => (
        <NavLink
          key={to}
          to={to}
          end={end}
          className={({ isActive }) =>
            `group relative text-[14px] font-medium px-3 h-10 flex items-center gap-3 rounded-lg transition-colors ${
              isActive ? 'text-white bg-white/[0.06]' : 'text-muted hover:text-label hover:bg-white/[0.03]'
            }`
          }
        >
          {({ isActive }) => (
            <>
              {isActive && <span className="absolute -left-3 top-2 bottom-2 w-[3px] rounded-r-full heat-bar" />}
              <Icon className={`w-[18px] h-[18px] ${isActive ? 'text-heat-4' : 'text-muted group-hover:text-label'}`} />
              {label}
            </>
          )}
        </NavLink>
      ))}
    </nav>
  );

  return (
    <div className="min-h-screen flex flex-col">
      <div className="h-[2px] heat-bar heat-shimmer shrink-0" />

      {/* Mobile top bar */}
      <div className="md:hidden sticky top-0 z-40 border-b border-engrave bg-surface/90 backdrop-blur">
        <div className="h-14 px-4 flex items-center justify-between">
          <Link to="/" className="flex items-center gap-2.5" aria-label="PlantDesk home">
            <Logo className="w-8 h-8" />
            <Wordmark className="text-[24px]" />
          </Link>
          <button
            className="btn h-9 w-10 px-0 justify-center"
            aria-expanded={menuOpen}
            aria-controls="mobile-menu"
            aria-label={menuOpen ? 'Close menu' : 'Open menu'}
            onClick={() => setMenuOpen((o) => !o)}
          >
            <svg viewBox="0 0 24 24" className="w-5 h-5" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round">
              {menuOpen ? <path d="M6 6l12 12M18 6 6 18" /> : <path d="M4 7h16M4 12h16M4 17h16" />}
            </svg>
          </button>
        </div>
        {menuOpen && (
          <div id="mobile-menu" className="border-t border-engrave pb-3">
            {links}
            <div className="px-6 pt-2 flex items-center justify-between">
              <span className="text-[13px] text-muted truncate">{user?.fullName} · {user && ROLE_LABEL[user.role]}</span>
              <button className="btn h-8" onClick={signOut}>Sign out</button>
            </div>
          </div>
        )}
      </div>

      <div className="flex-1 flex min-h-0">
        <aside className="hidden md:flex md:flex-col w-64 shrink-0 border-r border-engrave bg-[#0a0a0d]/80">
          <Link to="/" className="px-6 pt-6 pb-5 flex items-center gap-3 group" aria-label="PlantDesk home">
            <Logo className="w-9 h-9 shrink-0 drop-shadow-[0_0_14px_rgba(255,61,127,0.45)] transition-transform group-hover:scale-105" />
            <div className="min-w-0">
              <Wordmark className="text-[28px]" />
              <div className="text-[12px] text-muted mt-1 truncate" title={user?.plantName}>{user?.plantName}</div>
            </div>
          </Link>
          {links}
          <div className="mx-3 mt-4 p-4 rounded-xl border border-engrave bg-white/[0.02]">
            <div className="stencil text-[10px] text-muted">Plant code</div>
            <div className="data text-[16px] text-tag mt-0.5">{user?.plantCode}</div>
            <p className="text-[12px] text-muted mt-1.5 leading-snug">Everything here is scoped to this plant by the database itself.</p>
          </div>
          <FooterLinks className="mt-auto px-6 py-5" />
        </aside>

        <div className="flex-1 min-w-0 flex flex-col">
          <header className="hidden md:flex h-16 border-b border-engrave items-center justify-end gap-4 px-6">
            <div className="flex items-center gap-3 min-w-0">
              <span className="w-9 h-9 rounded-full grid place-items-center text-[12.5px] font-semibold text-white bg-gradient-to-br from-heat-1 via-heat-3 to-heat-4 shrink-0">
                {initials}
              </span>
              <span className="min-w-0 leading-tight">
                <span className="block text-[13.5px] font-medium truncate">{user?.fullName}</span>
                <span className="block text-[12px] text-heat-4">{user && ROLE_LABEL[user.role]}</span>
              </span>
            </div>
            <button className="btn h-9" onClick={signOut}>Sign out</button>
          </header>
          <main className="flex-1 p-4 sm:p-6 lg:p-8 min-w-0">{children}</main>
          <FooterLinks className="md:hidden px-4 pb-6" />
        </div>
      </div>
    </div>
  );
}
