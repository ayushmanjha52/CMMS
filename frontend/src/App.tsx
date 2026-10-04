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

export function App() {
  const { user, restoring } = useAuth();
  const can = useCan();

  if (restoring) {
    return <div className="h-full grid place-items-center stencil text-muted">Connecting…</div>;
  }
  if (!user) {
    return (
      <Routes>
        <Route path="*" element={<LoginPage />} />
      </Routes>
    );
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
        <Route path="/pm" element={<PmPage />} />
        <Route path="/spares" element={<PartsPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Layout>
  );
}
