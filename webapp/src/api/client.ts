import type {
  AuthUser,
  CreateTestInput,
  Test,
  TestRun,
  TestRunMetric,
  VirtualUser,
} from '../types';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api';

export class ApiError extends Error {
  status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

function readCookie(name: string): string | null {
  const match = document.cookie.split('; ').find((row) => row.startsWith(`${name}=`));
  return match ? decodeURIComponent(match.slice(name.length + 1)) : null;
}

async function toError(response: Response): Promise<ApiError> {
  let detail = response.statusText;
  try {
    const body = await response.json();
    if (body && typeof body.detail === 'string') {
      detail = body.detail;
    }
  } catch {
    // Non-JSON error body; fall back to the status text.
  }
  return new ApiError(detail, response.status);
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const method = (options.method ?? 'GET').toUpperCase();
  const headers = new Headers(options.headers);

  // Session cookie authenticates; the CSRF token guards every state-changing request.
  if (method !== 'GET' && method !== 'HEAD') {
    const csrf = readCookie('XSRF-TOKEN');
    if (csrf) {
      headers.set('X-XSRF-TOKEN', csrf);
    }
    if (options.body && !headers.has('Content-Type')) {
      headers.set('Content-Type', 'application/json');
    }
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    method,
    headers,
    credentials: 'include',
  });

  if (!response.ok) {
    throw await toError(response);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return response.json() as Promise<T>;
}

export const api = {
  async login(username: string, password: string): Promise<AuthUser> {
    const response = await fetch(`${API_BASE_URL}/auth/me`, {
      headers: { Authorization: `Basic ${btoa(`${username}:${password}`)}` },
      credentials: 'include',
    });
    if (!response.ok) {
      throw new ApiError(
        response.status === 401 ? 'Invalid username or password' : 'Login failed',
        response.status,
      );
    }
    return response.json() as Promise<AuthUser>;
  },

  me(): Promise<AuthUser> {
    return request<AuthUser>('/auth/me');
  },

  logout(): Promise<void> {
    return request<void>('/auth/logout', { method: 'POST' });
  },

  listTests(): Promise<Test[]> {
    return request<Test[]>('/tests');
  },

  getTest(id: string): Promise<Test> {
    return request<Test>(`/tests/${id}`);
  },

  createTest(input: CreateTestInput): Promise<Test> {
    return request<Test>('/tests', { method: 'POST', body: JSON.stringify(input) });
  },

  deleteTest(id: string): Promise<void> {
    return request<void>(`/tests/${id}`, { method: 'DELETE' });
  },

  listRuns(testId: string): Promise<TestRun[]> {
    return request<TestRun[]>(`/tests/${testId}/runs`);
  },

  startRun(testId: string): Promise<TestRun> {
    return request<TestRun>(`/tests/${testId}/runs`, { method: 'POST' });
  },

  getRun(runId: string): Promise<TestRun> {
    return request<TestRun>(`/runs/${runId}`);
  },

  getMetrics(runId: string): Promise<TestRunMetric[]> {
    return request<TestRunMetric[]>(`/runs/${runId}/metrics`);
  },

  getVirtualUsers(runId: string): Promise<VirtualUser[]> {
    return request<VirtualUser[]>(`/runs/${runId}/virtual-users`);
  },

  stopRun(runId: string): Promise<void> {
    return request<void>(`/runs/${runId}/stop`, { method: 'POST' });
  },
};