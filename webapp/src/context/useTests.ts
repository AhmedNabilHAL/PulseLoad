import { create } from 'zustand';
import { api } from '../api/client';
import type { CreateTestInput, Test } from '../types';

type TestsState = {
  tests: Test[];
  loading: boolean;
  error: string | null;
  fetchTests: () => Promise<void>;
  createTest: (input: CreateTestInput) => Promise<Test>;
  deleteTest: (id: string) => Promise<void>;
};

const useTests = create<TestsState>((set) => ({
  tests: [],
  loading: false,
  error: null,

  async fetchTests() {
    set({ loading: true, error: null });
    try {
      const tests = await api.listTests();
      set({ tests, loading: false });
    } catch (e) {
      set({ loading: false, error: e instanceof Error ? e.message : 'Failed to load tests' });
    }
  },

  async createTest(input) {
    const created = await api.createTest(input);
    set((state) => ({ tests: [...state.tests, created] }));
    return created;
  },

  async deleteTest(id) {
    await api.deleteTest(id);
    set((state) => ({ tests: state.tests.filter((t) => t.id !== id) }));
  },
}));

export default useTests;