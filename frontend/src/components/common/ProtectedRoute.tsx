import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';

interface ProtectedRouteProps {
  children: React.ReactNode;
  requiredRole?: string[];
}

export const ProtectedRoute: React.FC<ProtectedRouteProps> = ({ children, requiredRole }) => {
  const { isAuthenticated, isLoading, user } = useAuth();
  const location = useLocation();

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-950">
        <div className="flex flex-col items-center space-y-4">
          <div className="w-10 h-10 border-4 border-brand-500/20 border-t-brand-500 rounded-full animate-spin"></div>
          <p className="text-slate-400 text-sm font-medium">Verifying authorization...</p>
        </div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  if (requiredRole && user) {
    const userRoleNorm = (user.role || '').toUpperCase().replace('ROLE_', '');
    const hasRole = requiredRole.some((r) => r.toUpperCase().replace('ROLE_', '') === userRoleNorm);
    if (!hasRole) {
      return (
        <div className="min-h-screen flex items-center justify-center bg-slate-950 p-6">
          <div className="max-w-md w-full glass-panel p-8 rounded-xl border border-rose-500/30 text-center">
            <div className="w-12 h-12 rounded-full bg-rose-500/10 text-rose-400 flex items-center justify-center mx-auto mb-4 border border-rose-500/20">
              <span className="text-2xl font-bold">!</span>
            </div>
            <h2 className="text-xl font-bold text-slate-100 mb-2">Access Restricted</h2>
            <p className="text-slate-400 text-sm mb-6">
              Your role (<span className="text-rose-300 font-semibold">{user.role}</span>) does not have authorization to view this enterprise resource.
            </p>
            <a
              href="/dashboard"
              className="inline-block px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-200 text-sm font-medium rounded-lg transition"
            >
              Return to Dashboard
            </a>
          </div>
        </div>
      );
    }
  }

  return <>{children}</>;
};
