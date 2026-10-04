import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { refreshSession, setSessionListener } from '../api/client';
import type { Role, SessionUser } from '../api/types';

interface AuthState {
  user: SessionUser | null;
  restoring: boolean;
}

const AuthContext = createContext<AuthState>({ user: null, restoring: true });

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ user: null, restoring: true });

  useEffect(() => {
    setSessionListener((user) => setState({ user, restoring: false }));
    // A reload drops the in-memory access token; the refresh cookie restores the session.
    refreshSession().then((ok) => {
      if (!ok) setState({ user: null, restoring: false });
    });
  }, []);

  return <AuthContext.Provider value={state}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  return useContext(AuthContext);
}

/** UI hints only. Every one of these is enforced again by @PreAuthorize on the server. */
export function useCan() {
  const role: Role | undefined = useAuth().user?.role;
  return {
    manageWork: role === 'PLANT_ADMIN' || role === 'MAINTENANCE_MANAGER',
    doWork: role === 'PLANT_ADMIN' || role === 'MAINTENANCE_MANAGER' || role === 'TECHNICIAN',
    admin: role === 'PLANT_ADMIN',
    seeAnalytics: role !== 'TECHNICIAN',
    isTechnician: role === 'TECHNICIAN',
  };
}
