import React, { createContext, useContext, useState, useEffect } from 'react';
import { User } from '../types';
import {
  login as apiLogin,
  logout as apiLogout,
  getStoredUser,
  extractUserFromAuthResponse,
  LoginCredentials,
} from '../api/auth';
import { getStoredToken } from '../api/client';

export function isManagerRole(role?: string | null): boolean {
  if (!role) return false;
  const norm = role.toUpperCase().replace('ROLE_', '');
  return norm === 'PROCUREMENT_MANAGER' || norm === 'ADMIN';
}

export function isAdminRole(role?: string | null): boolean {
  if (!role) return false;
  const norm = role.toUpperCase().replace('ROLE_', '');
  return norm === 'ADMIN';
}

interface AuthContextType {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  isManager: boolean;
  isAdmin: boolean;
  login: (credentials: LoginCredentials) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<User | null>(() => getStoredUser());
  const [token, setToken] = useState<string | null>(() => getStoredToken());
  const [isLoading, setIsLoading] = useState(false);

  useEffect(() => {
    const handleUnauthorized = () => {
      setUser(null);
      setToken(null);
    };

    window.addEventListener('auth:unauthorized', handleUnauthorized);
    return () => {
      window.removeEventListener('auth:unauthorized', handleUnauthorized);
    };
  }, []);

  const login = async (credentials: LoginCredentials) => {
    const res = await apiLogin(credentials);
    const userObj = res.user || extractUserFromAuthResponse(res);
    setToken(res.token);
    setUser(userObj);
  };

  const logout = () => {
    apiLogout();
    setUser(null);
    setToken(null);
  };

  const isManager = isManagerRole(user?.role);
  const isAdmin = isAdminRole(user?.role);

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        isAuthenticated: !!token && !!user,
        isLoading,
        isManager,
        isAdmin,
        login,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = (): AuthContextType => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
