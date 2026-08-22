import React, { useState, useRef, useEffect } from 'react';
import { useAuth } from '../../context/AuthContext';
import { formatCurrency, formatRole } from '../../utils/format';
import {
  ChevronDown,
  Layers,
  CircleDot,
  LogOut,
} from 'lucide-react';

export const Header: React.FC = () => {
  const { user, logout } = useAuth();
  const [dropdownOpen, setDropdownOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setDropdownOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  return (
    <header className="h-16 border-b border-slate-800 bg-slate-950/80 backdrop-blur-md px-6 flex items-center justify-between sticky top-0 z-40">
      {/* Brand & Engine Indicator */}
      <div className="flex items-center space-x-4">
        <div className="flex items-center space-x-2.5">
          <div className="w-9 h-9 rounded-lg bg-gradient-to-tr from-brand-600 to-brand-400 flex items-center justify-center shadow-lg shadow-brand-500/20 text-white font-bold">
            <Layers className="w-5 h-5" />
          </div>
          <div>
            <div className="flex items-center space-x-2">
              <span className="font-bold text-slate-100 tracking-tight">AUTONOMOUS</span>
              <span className="text-brand-400 font-semibold tracking-wide text-xs px-1.5 py-0.5 rounded bg-brand-500/10 border border-brand-500/20">ENGINE</span>
            </div>
            <p className="text-[11px] text-slate-400 font-medium">Enterprise Core & AI Intelligence</p>
          </div>
        </div>

        <div className="hidden lg:flex items-center space-x-2 pl-6 border-l border-slate-800 text-xs text-slate-400">
          <CircleDot className="w-3.5 h-3.5 text-emerald-400 animate-pulse" />
          <span>Spring Core + Python AI Online</span>
        </div>
      </div>

      {/* User Controls */}
      <div className="flex items-center space-x-4">
        {user && (
          <div className="hidden md:flex items-center space-x-3 text-right">
            <div>
              <div className="text-xs font-semibold text-slate-200">{user.name}</div>
              <div className="text-[11px] text-slate-400 flex items-center justify-end space-x-1.5">
                <span className="text-brand-400 font-medium">{formatRole(user.role)}</span>
                <span>•</span>
                <span>Limit: {formatCurrency(user.authorizationLimit)}</span>
              </div>
            </div>
          </div>
        )}

        {/* User Dropdown */}
        <div className="relative" ref={dropdownRef}>
          <button
            onClick={() => setDropdownOpen(!dropdownOpen)}
            className="flex items-center space-x-2 p-1.5 rounded-lg border border-slate-800 hover:border-slate-700 bg-slate-900 text-slate-200 transition"
          >
            <div className="w-8 h-8 rounded-md bg-slate-800 text-brand-400 flex items-center justify-center font-bold text-sm">
              {user?.name ? user.name.charAt(0).toUpperCase() : 'U'}
            </div>
            <ChevronDown className="w-4 h-4 text-slate-400" />
          </button>

          {dropdownOpen && (
            <div className="absolute right-0 mt-2 w-64 glass-panel rounded-xl shadow-2xl py-2 border border-slate-800 text-slate-200 z-50 animate-in fade-in slide-in-from-top-2 duration-150">
              <div className="px-4 py-2.5 border-b border-slate-800/80">
                <p className="text-xs font-semibold text-slate-100">{user?.name}</p>
                <p className="text-[11px] text-slate-400 truncate">{user?.email}</p>
                <div className="mt-2 inline-flex items-center px-2 py-0.5 rounded text-[10px] font-medium bg-brand-500/10 text-brand-400 border border-brand-500/20">
                  {formatRole(user?.role)}
                </div>
              </div>

              <div className="px-4 py-2 text-xs text-slate-400 border-b border-slate-800/80">
                <div className="flex justify-between items-center py-1">
                  <span>Authorized Limit:</span>
                  <span className="font-semibold text-emerald-400">{formatCurrency(user?.authorizationLimit)}</span>
                </div>
              </div>

              <button
                onClick={() => {
                  setDropdownOpen(false);
                  logout();
                }}
                className="w-full px-4 py-2 text-left text-xs font-medium text-rose-400 hover:bg-rose-500/10 flex items-center space-x-2 transition"
              >
                <LogOut className="w-3.5 h-3.5" />
                <span>Sign Out</span>
              </button>
            </div>
          )}
        </div>
      </div>
    </header>
  );
};
