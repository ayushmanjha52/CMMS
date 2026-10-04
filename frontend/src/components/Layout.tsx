import type { ReactNode } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { logout } from '../api/client';
import { useAuth, useCan } from '../auth/AuthContext';

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
    can.seeAnalytics && { to: '/', label: 'Board', end: true },
    { to: '/work-orders', label: can.isTechnician ? 'My work orders' : 'Work orders' },
    { to: '/assets', label: 'Assets' },
    can.seeAnalytics && { to: '/pm', label: 'PM schedules' },
    { to: '/spares', label: 'Spares' },
  ].filter(Boolean) as { to: string; label: string; end?: boolean }[];

  return (
    <div className="min-h-full flex flex-col md:flex-row">
      <aside className="md:w-56 md:min-h-screen shrink-0 border-b md:border-b-0 md:border-r border-engrave bg-surface">
        <div className="px-4 py-3 border-b border-engrave">
          <div className="stencil text-[18px] leading-none">PlantDesk</div>
          <div className="data text-muted mt-1 truncate" title={user?.plantName}>
            {user?.plantCode} · {user?.plantName}
          </div>
        </div>
        <nav className="flex md:flex-col overflow-x-auto">
          {nav.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                `stencil text-[14px] px-4 h-9 flex items-center whitespace-nowrap border-l-2 ${
                  isActive ? 'border-label text-label bg-raised' : 'border-transparent text-muted hover:text-label'
                }`
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
      </aside>

      <div className="flex-1 min-w-0 flex flex-col">
        <header className="h-11 border-b border-engrave flex items-center justify-end gap-4 px-4">
          <span className="text-muted text-[13px] truncate">
            {user?.fullName} <span className="stencil text-[12px] ml-1">{user && ROLE_LABEL[user.role]}</span>
          </span>
          <button
            className="btn"
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
        <main className="flex-1 p-4 min-w-0">{children}</main>
      </div>
    </div>
  );
}
