import React, { useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { Layers, ShieldCheck, ArrowRight, Lock, Mail, AlertCircle } from 'lucide-react';

export const Login: React.FC = () => {
  const [email, setEmail] = useState('manager@procurement.com');
  const [password, setPassword] = useState('password123');
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const from = (location.state as any)?.from?.pathname || '/dashboard';

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setIsSubmitting(true);

    try {
      await login({ email, password });
      navigate(from, { replace: true });
    } catch (err: any) {
      setError(err.message || 'Authentication failed. Please verify your credentials.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleQuickLogin = (quickEmail: string) => {
    setEmail(quickEmail);
    setPassword('password123');
  };

  return (
    <div className="min-h-screen bg-slate-950 flex flex-col justify-center items-center p-4 selection:bg-brand-500 selection:text-white">
      {/* Background ambient glow */}
      <div className="absolute top-1/4 left-1/2 -translate-x-1/2 w-96 h-96 bg-brand-600/10 rounded-full blur-3xl pointer-events-none"></div>

      <div className="max-w-md w-full glass-panel rounded-2xl p-8 border border-slate-800 relative z-10 shadow-2xl space-y-6">
        {/* Logo & Header */}
        <div className="text-center space-y-2">
          <div className="w-12 h-12 rounded-xl bg-gradient-to-tr from-brand-600 to-brand-400 flex items-center justify-center mx-auto shadow-lg shadow-brand-500/25 text-white">
            <Layers className="w-6 h-6" />
          </div>
          <h1 className="text-2xl font-bold text-slate-100 tracking-tight">Autonomous Procurement</h1>
          <p className="text-xs text-slate-400">
            Sign in to access the authoritative procurement engine & AI intelligence service
          </p>
        </div>

        {/* Error Alert */}
        {error && (
          <div className="p-3 rounded-lg bg-rose-500/10 border border-rose-500/30 flex items-center space-x-2.5 text-rose-400 text-xs">
            <AlertCircle className="w-4 h-4 flex-shrink-0" />
            <span>{error}</span>
          </div>
        )}

        {/* Login Form */}
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-xs font-semibold text-slate-300 mb-1.5 flex items-center space-x-1.5">
              <Mail className="w-3.5 h-3.5 text-slate-400" />
              <span>Corporate Email</span>
            </label>
            <input
              type="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="name@procurement.com"
              className="w-full px-3.5 py-2.5 bg-slate-900 border border-slate-800 rounded-lg text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:border-brand-500 transition"
              disabled={isSubmitting}
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-300 mb-1.5 flex items-center space-x-1.5">
              <Lock className="w-3.5 h-3.5 text-slate-400" />
              <span>Password</span>
            </label>
            <input
              type="password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••••••"
              className="w-full px-3.5 py-2.5 bg-slate-900 border border-slate-800 rounded-lg text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:border-brand-500 transition"
              disabled={isSubmitting}
            />
          </div>

          <button
            type="submit"
            disabled={isSubmitting}
            className="w-full py-2.5 px-4 bg-brand-600 hover:bg-brand-500 text-white font-bold rounded-lg text-sm transition duration-150 flex items-center justify-center space-x-2 shadow-lg shadow-brand-500/25 disabled:opacity-50"
          >
            {isSubmitting ? (
              <div className="w-4 h-4 border-2 border-white/20 border-t-white rounded-full animate-spin"></div>
            ) : (
              <>
                <span>Sign In to Engine</span>
                <ArrowRight className="w-4 h-4" />
              </>
            )}
          </button>
        </form>

        {/* Demo Fast Login Shortcuts */}
        <div className="pt-4 border-t border-slate-800/80 space-y-2">
          <p className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider text-center">
            Demo Personas (Pre-Seeded)
          </p>
          <div className="grid grid-cols-3 gap-2 text-center text-xs">
            <button
              type="button"
              onClick={() => handleQuickLogin('manager@procurement.com')}
              className="p-2 rounded-lg bg-slate-900 hover:bg-slate-800 border border-slate-800 text-slate-300 transition"
            >
              <div className="font-semibold text-[11px] text-brand-300">Manager</div>
              <div className="text-[10px] text-slate-400">₹4.5L Limit</div>
            </button>
            <button
              type="button"
              onClick={() => handleQuickLogin('user@procurement.com')}
              className="p-2 rounded-lg bg-slate-900 hover:bg-slate-800 border border-slate-800 text-slate-300 transition"
            >
              <div className="font-semibold text-[11px] text-slate-200">Employee</div>
              <div className="text-[10px] text-slate-400">₹50K Limit</div>
            </button>
            <button
              type="button"
              onClick={() => handleQuickLogin('admin@procurement.com')}
              className="p-2 rounded-lg bg-slate-900 hover:bg-slate-800 border border-slate-800 text-slate-300 transition"
            >
              <div className="font-semibold text-[11px] text-purple-300">Admin</div>
              <div className="text-[10px] text-slate-400">Full Access</div>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
