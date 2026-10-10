import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth, useCan } from './auth/AuthContext';
import { Layout } from './components/Layout';
import { LoginPage } from './pages/LoginPage';
import { BoardPage } from './pages/BoardPage';
import { WorkOrdersPage } from './pages/WorkOrdersPage';
import { WorkOrderPage } from './pages/WorkOrderPage';
import { RaiseWorkOrderPage } from './pages/RaiseWorkOrderPage';
import { AssetsPage } from './pages/AssetsPage';
import { AssetPage } from './pages/AssetPage';
import { PmPage } from './pages/PmPage';
import { PartsPage } from './pages/PartsPage';
import { NotFoundPage, PrivacyPage, TermsPage } from './pages/LegalPages';

export function App() {
  return (
    <Routes>
      {/* Public whether or not you are signed in. */}
      <Route path="/privacy" element={<PrivacyPage />} />
      <Route path="/terms" element={<TermsPage />} />
      <Route path="*" element={<Workspace />} />
    </Routes>
  );
}

function Workspace() {
  const { user, restoring } = useAuth();
  const can = useCan();

  if (restoring) {
    return (
      <div className="h-screen grid place-items-center text-muted">
        <span className="flex items-center gap-3">
          <span className="w-2 h-2 rounded-full bg-heat-3 pulse-dot" />
          Connecting…
        </span>
      </div>
    );
  }
  if (!user) {
    return <LoginPage />;
  }
  return (
    <Layout>
      <Routes>
        <Route path="/" element={can.seeAnalytics ? <BoardPage /> : <Navigate to="/work-orders" replace />} />
        <Route path="/work-orders" element={<WorkOrdersPage />} />
        <Route path="/work-orders/raise" element={<RaiseWorkOrderPage />} />
        <Route path="/work-orders/:id" element={<WorkOrderPage />} />
        <Route path="/assets" element={<AssetsPage />} />
        <Route path="/assets/:id" element={<AssetPage />} />
        <Route path="/pm" element={can.seeAnalytics ? <PmPage /> : <NotFoundPage />} />
        <Route path="/spares" element={<PartsPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </Layout>
  );
}
