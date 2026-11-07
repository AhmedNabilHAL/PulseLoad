
import type { Test } from '../types';

import { create } from 'zustand'

const mockTestList: Test[] = [
  { id: crypto.randomUUID(), testName: 'Test 1', targetUrl: 'backendserver.com/products', httpMethod: 'GET', durationInSeconds: 1, requestsPerSec: 0 },
  { id: crypto.randomUUID(), testName: 'Test 2', targetUrl: 'https://backendserver.com/products', httpMethod: 'POST', durationInSeconds: 2, requestsPerSec: 0 },
  { id: crypto.randomUUID(), testName: 'Test 3', targetUrl: 'http://backendserver.com/products/1', httpMethod: 'DELETE', durationInSeconds: 3, requestsPerSec: 0 },
];

const useTests = create<{
  tests: Test[];
  addTest: (newTest: Test) => void;
  removeAllTests: () => void;
  updateTests: (newTests: Test[]) => void;
}>((set) => ({
  tests: mockTestList,
  addTest: (newTest) => set((state) => ({ tests: [...state.tests, newTest] })),
  removeAllTests: () => set({ tests: [] }),
  updateTests: (newTests) => set({ tests: newTests }),
}))

export default useTests;