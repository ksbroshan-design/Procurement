import { springClient, setStoredToken, removeStoredToken } from './client';
import { AuthResponse, User } from '../types';

export interface LoginCredentials {
  email: string;
  password: string;
}

export function extractUserFromAuthResponse(authData: AuthResponse | any): User {
  const userId = authData.userId || authData.id || '';
  const role = authData.role || 'USER';
  return {
    id: userId,
    userId: userId,
    email: authData.email || '',
    name: authData.name || '',
    role: role,
    authorizationLimit:
      typeof authData.authorizationLimit === 'number'
        ? authData.authorizationLimit
        : Number(authData.authorizationLimit) || 0,
    tokenType: authData.tokenType || 'Bearer',
    expiresInMs: authData.expiresInMs || 86400000,
  };
}

export async function login(credentials: LoginCredentials): Promise<AuthResponse> {
  const response = await springClient.post<AuthResponse>('/api/auth/login', credentials);
  if (response && response.token) {
    setStoredToken(response.token);
    if (response.tokenType) {
      localStorage.setItem('procurement_token_type', response.tokenType);
    }
    const user = response.user ? response.user : extractUserFromAuthResponse(response);
    localStorage.setItem('procurement_user', JSON.stringify(user));
    return {
      ...response,
      user,
    };
  }
  return response;
}

export function logout(): void {
  removeStoredToken();
  localStorage.removeItem('procurement_user');
  localStorage.removeItem('procurement_token_type');
}

export function getStoredUser(): User | null {
  const userStr = localStorage.getItem('procurement_user');
  if (!userStr || userStr === 'undefined' || userStr === 'null') return null;
  try {
    return JSON.parse(userStr) as User;
  } catch (err) {
    return null;
  }
}
