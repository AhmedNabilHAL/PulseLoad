import { useState } from 'react';
import type { FormEvent } from 'react';
import useAuth from '../context/useAuth';

function LoginForm() {
  const { login, error } = useAuth();
  const [username, setUsername] = useState('operator');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setSubmitting(true);
    try {
      await login(username, password);
    } catch {
      // Error message is surfaced through the auth store.
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <main className="mx-auto mt-16 max-w-sm px-6">
      <h1 className="mb-1 text-2xl font-semibold text-gray-900">PulseLoad</h1>
      <p className="mb-6 text-sm text-gray-600">Sign in to create and run load tests.</p>

      <form onSubmit={handleSubmit} className="flex flex-col gap-3">
        <label htmlFor="username" className="text-sm font-medium">Username</label>
        <input
          id="username"
          name="username"
          autoComplete="username"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          required
          className="rounded border border-gray-300 px-3 py-2 focus:outline-none focus:ring-2 focus:ring-indigo-500"
        />

        <label htmlFor="password" className="text-sm font-medium">Password</label>
        <input
          id="password"
          name="password"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
          className="rounded border border-gray-300 px-3 py-2 focus:outline-none focus:ring-2 focus:ring-indigo-500"
        />

        {error && (
          <p role="alert" className="text-sm text-red-700">
            {error}
          </p>
        )}

        <button
          type="submit"
          disabled={submitting}
          className="mt-1 rounded bg-indigo-600 px-3 py-2 font-medium text-white hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-indigo-500 disabled:opacity-60"
        >
          {submitting ? 'Signing in…' : 'Sign in'}
        </button>
      </form>

      <p className="mt-6 text-xs text-gray-500">
        Workshop accounts: <code>viewer</code>, <code>operator</code>, <code>admin</code> (password = username).
      </p>
    </main>
  );
}

export default LoginForm;
