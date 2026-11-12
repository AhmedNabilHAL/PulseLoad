import { create } from 'zustand';
import { ApiError, api } from '../api/client';
import type { AuthUser } from '../types';

type AuthState = {
  user: AuthUser | null;
  initializing: boolean;
  error: string | null;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  refresh: () => Promise<void>;
};

const useAuth = create<AuthState>((set) => ({
  user: null,
  initializing: true,
  error: null,

  async login(username, password) {
    set({ error: null });
    try {
      const user = await api.login(username, password);
      set({ user });
    } catch (e) {
      set({ error: e instanceof ApiError ? e.message : 'Login failed' });
      throw e;
    }
  },

  async logout() {
    try {
      await api.logout();
    } finally {
      set({ user: null });
    }
  },

  // Re-establishes identity from an existing session cookie on page load.
  async refresh() {
    try {
      const user = await api.me();
      set({ user, initializing: false });
    } catch {
      set({ user: null, initializing: false });
    }
  },
}));

export default useAuth;
