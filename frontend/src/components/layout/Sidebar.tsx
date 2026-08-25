import React, { useState, useEffect } from 'react';
import { NavLink } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { getPendingApprovals } from '../../api/approval';
import {
  LayoutDashboard,
  Sparkles,
  Layers,
  CheckSquare,
  ShoppingBag,
  Cpu,
} from 'lucide-react';

export const Sidebar: React.FC = () => {
  const { isManager, user } = useAuth();
  const [pendingCount, setPendingCount] = useState<number>(0);

  useEffect(() => {
    if (isManager) {
      getPendingApprovals()
        .then((res) => {
          if (Array.isArray(res)) setPendingCount(res.length);
        })
        .catch(() => {});
    }
  }, [isManager]);

  const navItems = [
    {
      to: '/dashboard',
      label: 'Dashboard',
      icon: LayoutDashboard,
      roles: ['USER', 'ROLE_USER', 'PROCUREMENT_MANAGER', 'ROLE_PROCUREMENT_MANAGER', 'ADMIN', 'ROLE_ADMIN'],
    },
    {
      to: '/procure',
      label: 'New AI Procurement',
      icon: Sparkles,
      highlight: true,
      roles: ['USER', 'ROLE_USER', 'PROCUREMENT_MANAGER', 'ROLE_PROCUREMENT_MANAGER', 'ADMIN', 'ROLE_ADMIN'],
    },
    {
      to: '/procurements',
      label: 'Procurements',
      icon: Layers,
      roles: ['USER', 'ROLE_USER', 'PROCUREMENT_MANAGER', 'ROLE_PROCUREMENT_MANAGER', 'ADMIN', 'ROLE_ADMIN'],
    },
    {
      to: '/approvals',
      label: 'Approvals',
      icon: CheckSquare,
      badge: pendingCount > 0 ? pendingCount : undefined,
      roles: ['PROCUREMENT_MANAGER', 'ROLE_PROCUREMENT_MANAGER', 'ADMIN', 'ROLE_ADMIN'],
    },
    {
      to: '/purchase-orders',
      label: 'Purchase Orders',
      icon: ShoppingBag,
      roles: ['USER', 'ROLE_USER', 'PROCUREMENT_MANAGER', 'ROLE_PROCUREMENT_MANAGER', 'ADMIN', 'ROLE_ADMIN'],
    },
  ];

  const userRoleNorm = (user?.role || '').toUpperCase().replace('ROLE_', '');
  const visibleItems = navItems.filter((item) => {
    if (!item.roles) return true;
    if (!user) return false;
    return item.roles.some((r) => r.toUpperCase().replace('ROLE_', '') === userRoleNorm);
  });

  return (
    <aside className="w-64 border-r border-slate-800 bg-slate-950/60 backdrop-blur-sm flex flex-col justify-between p-4 min-h-[calc(100vh-4rem)]">
      <div className="space-y-6">
        <div>
          <p className="px-3 text-[11px] font-semibold text-slate-400 uppercase tracking-wider mb-3">
            Core Modules
          </p>
          <nav className="space-y-1">
            {visibleItems.map((item) => {
              const Icon = item.icon;
              return (
                <NavLink
                  key={item.to}
                  to={item.to}
                  className={({ isActive }) =>
                    "flex items-center justify-between px-3 py-2.5 rounded-lg text-xs font-medium transition duration-150 " +
                    (isActive
                      ? item.highlight
                        ? "bg-brand-600 text-white shadow-lg shadow-brand-500/20"
                        : "bg-slate-800/80 text-brand-400 border border-slate-700/80"
                      : item.highlight
                      ? "text-brand-300 hover:bg-brand-500/10 hover:text-white border border-brand-500/20"
                      : "text-slate-400 hover:bg-slate-900 hover:text-slate-200")
                  }
                >
                  <div className="flex items-center space-x-3">
                    <Icon className="w-4 h-4" />
                    <span>{item.label}</span>
                  </div>
                  {item.badge !== undefined && item.badge > 0 && (
                    <span className="px-1.5 py-0.5 text-[10px] font-bold rounded-full bg-amber-500/20 text-amber-300 border border-amber-500/30">
                      {item.badge}
                    </span>
                  )}
                </NavLink>
              );
            })}
          </nav>
        </div>
      </div>

      {/* Enterprise Architectural Invariant Footer */}
      <div className="p-3 rounded-lg border border-slate-800/80 bg-slate-900/40 text-[11px] text-slate-400 space-y-1.5">
        <div className="flex items-center space-x-1.5 font-semibold text-slate-300">
          <Cpu className="w-3.5 h-3.5 text-brand-400" />
          <span>System Topology</span>
        </div>
        <div className="text-[10px] text-slate-400 leading-relaxed">
          <p><span className="text-slate-300 font-medium">FastAPI:</span> NLP & Clarification</p>
          <p><span className="text-slate-300 font-medium">Spring Boot:</span> Authoritative Engine</p>
        </div>
      </div>
    </aside>
  );
};
