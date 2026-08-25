import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import { ProtectedRoute } from './components/common/ProtectedRoute';
import { AppLayout } from './components/layout/AppLayout';

import { Login } from './pages/Login';
import { Dashboard } from './pages/Dashboard';
import { NewProcurement } from './pages/NewProcurement';
import { Procurements } from './pages/Procurements';
import { ProcurementDetails } from './pages/ProcurementDetails';
import { Approvals } from './pages/Approvals';
import { PurchaseOrder } from './pages/PurchaseOrder';

export const App: React.FC = () => {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          {/* Public Authentication Route */}
          <Route path="/login" element={<Login />} />

          {/* Protected Application Routes */}
          <Route
            element={
              <ProtectedRoute>
                <AppLayout />
              </ProtectedRoute>
            }
          >
            <Route path="/" element={<Navigate to="/dashboard" replace />} />
            <Route path="/dashboard" element={<Dashboard />} />
            <Route path="/procure" element={<NewProcurement />} />
            <Route path="/procurements" element={<Procurements />} />
            <Route path="/procurements/:id" element={<ProcurementDetails />} />
            <Route
              path="/approvals"
              element={
                <ProtectedRoute requiredRole={['ROLE_PROCUREMENT_MANAGER', 'ROLE_ADMIN']}>
                  <Approvals />
                </ProtectedRoute>
              }
            />
            <Route path="/purchase-orders" element={<PurchaseOrder />} />
            <Route path="/purchase-orders/:id" element={<PurchaseOrder />} />
          </Route>

          {/* Catch-all Fallback */}
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
};

export default App;
