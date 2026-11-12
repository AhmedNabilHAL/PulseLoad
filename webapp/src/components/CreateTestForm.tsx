import { useState } from 'react';
import type { FormEvent } from 'react';
import { ApiError } from '../api/client';
import useTests from '../context/useTests';
import { VALID_HTTP_METHODS, type CreateTestInput, type HttpMethod } from '../types';

function CreateTestForm({ onCreated }: { onCreated?: () => void }) {
  const { createTest } = useTests();
  const [testName, setTestName] = useState('');
  const [targetUrl, setTargetUrl] = useState('');
  const [httpMethod, setHttpMethod] = useState<HttpMethod>('GET');
  const [virtualUsers, setVirtualUsers] = useState(5);
  const [durationSeconds, setDurationSeconds] = useState(10);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setError(null);

    if (!testName.trim()) {
      setError('Test name is required.');
      return;
    }
    if (!/^https?:\/\//i.test(targetUrl.trim())) {
      setError('Target URL must start with http:// or https://');
      return;
    }
    if (virtualUsers < 1) {
      setError('Virtual users must be at least 1.');
      return;
    }
    if (durationSeconds < 1) {
      setError('Duration must be at least 1 second.');
      return;
    }

    const input: CreateTestInput = {
      testName: testName.trim(),
      targetUrl: targetUrl.trim(),
      httpMethod,
      workloadProfile: {
        loadModel: 'CLOSED',
        startTarget: virtualUsers,
        maxConcurrency: virtualUsers,
        stages: [{ durationSeconds, target: virtualUsers }],
      },
    };

    setSubmitting(true);
    try {
      await createTest(input);
      onCreated?.();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to create test.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
      <div className="flex flex-col gap-1">
        <label htmlFor="testName" className="text-sm font-medium">Test name</label>
        <input id="testName" name="testName" value={testName} onChange={(e) => setTestName(e.target.value)}
          className="rounded border border-gray-300 px-3 py-2" required />
      </div>

      <div className="flex flex-col gap-1">
        <label htmlFor="targetUrl" className="text-sm font-medium">Target URL</label>
        <input id="targetUrl" name="targetUrl" value={targetUrl} onChange={(e) => setTargetUrl(e.target.value)}
          placeholder="https://example.com/health" className="rounded border border-gray-300 px-3 py-2" required />
      </div>

      <div className="flex flex-col gap-1">
        <label htmlFor="httpMethod" className="text-sm font-medium">HTTP method</label>
        <select id="httpMethod" name="httpMethod" value={httpMethod}
          onChange={(e) => setHttpMethod(e.target.value as HttpMethod)}
          className="rounded border border-gray-300 px-3 py-2">
          {VALID_HTTP_METHODS.map((method) => (
            <option key={method} value={method}>{method}</option>
          ))}
        </select>
      </div>

      <div className="flex gap-4">
        <div className="flex flex-1 flex-col gap-1">
          <label htmlFor="virtualUsers" className="text-sm font-medium">Virtual users</label>
          <input id="virtualUsers" name="virtualUsers" type="number" min={1} value={virtualUsers}
            onChange={(e) => setVirtualUsers(Number(e.target.value))}
            className="rounded border border-gray-300 px-3 py-2" />
        </div>
        <div className="flex flex-1 flex-col gap-1">
          <label htmlFor="durationSeconds" className="text-sm font-medium">Duration (seconds)</label>
          <input id="durationSeconds" name="durationSeconds" type="number" min={1} value={durationSeconds}
            onChange={(e) => setDurationSeconds(Number(e.target.value))}
            className="rounded border border-gray-300 px-3 py-2" />
        </div>
      </div>

      {error && <p role="alert" className="text-sm text-red-700">{error}</p>}

      <button type="submit" disabled={submitting}
        className="rounded bg-indigo-600 px-3 py-2 font-medium text-white hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-indigo-500 disabled:opacity-60">
        {submitting ? 'Creating…' : 'Create test'}
      </button>
    </form>
  );
}

export default CreateTestForm;