import type { ReactNode } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { logout } from '../api/client';
import { useAuth, useCan } from '../auth/AuthContext';
import { IconBoard, IconBox, IconCalendar, IconTree, IconWrench, Logo } from './icons';

const ROLE_LABEL = {
  PLANT_ADMIN: 'Plant admin',
  MAINTENANCE_MANAGER: 'Maintenance manager',
  TECHNICIAN: 'Technician',
  VIEWER: 'Viewer',
} as const;

export function Layout({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const can = useCan();
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  const nav = [
    can.seeAnalytics && { to: '/', label: 'Board', end: true, icon: IconBoard },
    { to: '/work-orders', label: can.isTechnician ? 'My work orders' : 'Work orders', icon: IconWrench },
    { to: '/assets', label: 'Assets', icon: IconTree },
    can.seeAnalytics && { to: '/pm', label: 'PM schedules', icon: IconCalendar },
    { to: '/spares', label: 'Spares', icon: IconBox },
  ].filter(Boolean) as { to: string; label: string; end?: boolean; icon: typeof IconBoard }[];

  const initials = (user?.fullName ?? '?').split(' ').map((p) => p[0]).slice(0, 2).join('');

  return (
    <div className="min-h-screen flex flex-col">
      {/* The heat stripe: the one brand line across the top of every screen. */}
      <div className="h-[3px] heat-bar heat-shimmer shrink-0" />
      <div className="flex-1 flex flex-col md:flex-row min-h-0">
        <aside className="md:w-60 md:min-h-[calc(100vh-3px)] shrink-0 border-b md:border-b-0 md:border-r border-engrave bg-gradient-to-b from-[#1b1145] via-[#130c33] to-[#0d0822]">
          <div className="px-4 py-4 flex items-center gap-3 border-b border-engrave/70">
            <Logo className="w-9 h-9 shrink-0 drop-shadow-[0_0_12px_rgba(255,61,127,0.55)]" />
            <div className="min-w-0">
              <div className="stencil text-[20px] leading-none heat-text">PlantDesk</div>
              <div className="text-[11.5px] text-muted mt-1 truncate" title={user?.plantName}>
                {user?.plantName}
              </div>
            </div>
          </div>

          <nav className="flex md:flex-col overflow-x-auto md:px-2 md:py-3 md:gap-1">
            {nav.map(({ to, label, end, icon: Icon }) => (
              <NavLink
                key={to}
                to={to}
                end={end}
                className={({ isActive }) =>
                  `group relative stencil text-[14px] px-3 h-10 flex items-center gap-3 whitespace-nowrap md:rounded-lg transition-colors ${
                    isActive
                      ? 'text-white bg-gradient-to-r from-heat-3/25 via-heat-2/15 to-transparent'
                      : 'text-muted hover:text-label hover:bg-white/[0.04]'
                  }`
                }
              >
                {({ isActive }) => (
                  <>
                    {isActive && <span className="absolute left-0 top-2 bottom-2 w-[3px] rounded-full heat-bar hidden md:block" />}
                    <Icon className={`w-[18px] h-[18px] ${isActive ? 'text-heat-4' : 'text-muted group-hover:text-label'}`} />
                    {label}
                  </>
                )}
              </NavLink>
            ))}
          </nav>

          <div className="hidden md:block mx-3 mt-6 p-3 rounded-xl border border-engrave bg-white/[0.03]">
            <div className="stencil text-[11px] text-muted">Plant code</div>
            <div className="data text-[15px] text-tag">{user?.plantCode}</div>
            <div className="text-[11.5px] text-muted mt-1 leading-snug">Everything you see is scoped to this plant by the database itself.</div>
          </div>
        </aside>

        <div className="flex-1 min-w-0 flex flex-col">
          <header className="h-14 border-b border-engrave/70 flex items-center justify-end gap-3 px-4 bg-[#0f0a24]/60 backdrop-blur">
            <div className="flex items-center gap-3 min-w-0">
              <span className="w-8 h-8 rounded-full grid place-items-center text-[12px] font-semibold text-white bg-gradient-to-br from-heat-1 via-heat-3 to-heat-4 shrink-0">
                {initials}
              </span>
              <span className="min-w-0 leading-tight">
                <span className="block text-[13px] truncate">{user?.fullName}</span>
                <span className="block stencil text-[10.5px] text-heat-4">{user && ROLE_LABEL[user.role]}</span>
              </span>
            </div>
            <button
              className="btn h-8"
              onClick={async () => {
                // Explicit sign-out returns to the root, so the next person at a shared shift
                // terminal does not land on the previous user's page.
                navigate('/', { replace: true });
                await logout();
                queryClient.clear();
              }}
            >
              Sign out
            </button>
          </header>
          <main className="flex-1 p-4 md:p-6 min-w-0">{children}</main>
        </div>
      </div>
    </div>
  );
}
