export class ApiError extends Error {
  statusCode: number;
  details?: any;

  constructor(message: string, statusCode: number, details?: any) {
    super(message);
    this.name = 'ApiError';
    this.statusCode = statusCode;
    this.details = details;
  }
}

export class AuthenticationError extends ApiError {
  constructor(message = 'Session expired or unauthenticated. Please log in.', details?: any) {
    super(message, 401, details);
    this.name = 'AuthenticationError';
  }
}

export class AuthorizationError extends ApiError {
  constructor(message = 'You do not have permission to perform this action.', details?: any) {
    super(message, 403, details);
    this.name = 'AuthorizationError';
  }
}

export class StateConflictError extends ApiError {
  constructor(message = 'State conflict occurred on backend.', details?: any) {
    super(message, 409, details);
    this.name = 'StateConflictError';
  }
}

export const SPRING_API_URL = import.meta.env.VITE_SPRING_API_URL || 'http://localhost:8080';
export const PYTHON_API_URL = import.meta.env.VITE_PYTHON_API_URL || 'http://localhost:8000';

export function getStoredToken(): string | null {
  return localStorage.getItem('procurement_jwt');
}

export function setStoredToken(token: string): void {
  localStorage.setItem('procurement_jwt', token);
}

export function removeStoredToken(): void {
  localStorage.removeItem('procurement_jwt');
  localStorage.removeItem('procurement_token_type');
  localStorage.removeItem('procurement_user');
}

interface RequestOptions extends RequestInit {
  token?: string | null;
}

export async function request<T>(
  baseUrl: string,
  endpoint: string,
  options: RequestOptions = {}
): Promise<T> {
  const url = baseUrl + endpoint;
  const token = options.token !== undefined ? options.token : getStoredToken();
  const tokenType = localStorage.getItem('procurement_token_type') || 'Bearer';

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...((options.headers as Record<string, string>) || {}),
  };

  if (token) {
    headers['Authorization'] = tokenType + ' ' + token;
  }

  try {
    const response = await fetch(url, {
      ...options,
      headers,
    });

    let data: any = null;
    const contentType = response.headers.get('content-type');
    if (contentType && contentType.includes('application/json')) {
      try {
        data = await response.json();
      } catch (err) {
        data = null;
      }
    } else {
      data = await response.text();
    }

    if (response.ok) {
      if (data && typeof data === 'object' && 'data' in data && 'success' in data) {
        return data.data as T;
      }
      return data as T;
    }

    const errorMessage =
      (data && typeof data === 'object' && (data.message || data.error || data.detail)) ||
      'HTTP ' + response.status + ': Request failed';

    if (response.status === 401) {
      removeStoredToken();
      window.dispatchEvent(new CustomEvent('auth:unauthorized'));
      throw new AuthenticationError(errorMessage, data);
    }

    if (response.status === 403) {
      throw new AuthorizationError(errorMessage, data);
    }

    if (response.status === 409) {
      throw new StateConflictError(errorMessage, data);
    }

    throw new ApiError(errorMessage, response.status, data);
  } catch (error: any) {
    if (error instanceof ApiError) {
      throw error;
    }
    throw new ApiError(
      error.message || 'Network connection to backend failed. Please check if services are running.',
      0,
      error
    );
  }
}

export const springClient = {
  get: <T>(endpoint: string, options?: RequestOptions) =>
    request<T>(SPRING_API_URL, endpoint, { ...options, method: 'GET' }),
  post: <T>(endpoint: string, body?: any, options?: RequestOptions) =>
    request<T>(SPRING_API_URL, endpoint, {
      ...options,
      method: 'POST',
      body: body ? JSON.stringify(body) : undefined,
    }),
  put: <T>(endpoint: string, body?: any, options?: RequestOptions) =>
    request<T>(SPRING_API_URL, endpoint, {
      ...options,
      method: 'PUT',
      body: body ? JSON.stringify(body) : undefined,
    }),
  delete: <T>(endpoint: string, options?: RequestOptions) =>
    request<T>(SPRING_API_URL, endpoint, { ...options, method: 'DELETE' }),
};

export const pythonClient = {
  get: <T>(endpoint: string, options?: RequestOptions) =>
    request<T>(PYTHON_API_URL, endpoint, { ...options, method: 'GET' }),
  post: <T>(endpoint: string, body?: any, options?: RequestOptions) =>
    request<T>(PYTHON_API_URL, endpoint, {
      ...options,
      method: 'POST',
      body: body ? JSON.stringify(body) : undefined,
    }),
};
