import { useCallback, useEffect, useState } from 'react';
import { NavLink, useParams } from 'react-router-dom';
import { ApiError, api } from '../api/client';
import useAuth from '../context/useAuth';
import {
  hasRole,
  isTerminal,
  type ExecutionOutcome,
  type Test,
  type TestRun,
  type TestRunMetric,
  type TestRunStatus,
  type VirtualUser,
  type VirtualUserActivity,
} from '../types';

const RUN_STATUS_STYLES: Record<TestRunStatus, string> = {
  QUEUED: 'bg-gray-200 text-gray-800',
  RUNNING: 'bg-blue-200 text-blue-900',
  COMPLETED: 'bg-green-200 text-green-900',
  FAILED: 'bg-red-200 text-red-900',
  CANCELLED: 'bg-amber-200 text-amber-900',
};

const VU_STATE_STYLES: Record<VirtualUserActivity, string> = {
  IDLE: 'bg-gray-200 text-gray-800',
  REQUESTING: 'bg-blue-200 text-blue-900',
  COMPLETED: 'bg-green-200 text-green-900',
  ERROR: 'bg-red-200 text-red-900',
};

const FAILED_OUTCOMES: ExecutionOutcome[] = [
  'CLIENT_ERROR_4XX',
  'SERVER_ERROR_5XX',
  'TIMEOUT',
  'CONNECT_ERROR',
];

function sumFailed(counts?: Partial<Record<ExecutionOutcome, number>> | null): number {
  if (!counts) return 0;
  return FAILED_OUTCOMES.reduce((total, outcome) => total + (counts[outcome] ?? 0), 0);
}

function formatMs(nanos?: number | null): string {
  if (nanos == null) return '—';
  return `${(nanos / 1_000_000).toFixed(1)} ms`;
}

function formatTime(iso?: string | null): string {
  return iso ? new Date(iso).toLocaleTimeString() : '—';
}

function TestPage() {
  const { id } = useParams<{ id: string }>();
  const { user } = useAuth();
  const canOperate = hasRole(user, 'OPERATOR', 'ADMIN');

  const [test, setTest] = useState<Test | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [runs, setRuns] = useState<TestRun[]>([]);
  const [selectedRunId, setSelectedRunId] = useState<string | null>(null);
  const [run, setRun] = useState<TestRun | null>(null);
  const [metrics, setMetrics] = useState<TestRunMetric[]>([]);
  const [vus, setVus] = useState<VirtualUser[]>([]);
  const [actionError, setActionError] = useState<string | null>(null);

  const loadTestAndRuns = useCallback(async () => {
    if (!id) return;
    try {
      const [loadedTest, loadedRuns] = await Promise.all([api.getTest(id), api.listRuns(id)]);
      setTest(loadedTest);
      setRuns(loadedRuns);
    } catch (e) {
      setLoadError(e instanceof ApiError ? e.message : 'Failed to load test.');
    }
  }, [id]);

  useEffect(() => {
    loadTestAndRuns();
  }, [loadTestAndRuns]);

  // Poll the selected run's state, metrics, and virtual users until it reaches a terminal status.
  useEffect(() => {
    if (!selectedRunId) return;
    let active = true;
    let timer: number | undefined;

    const poll = async () => {
      try {
        const [latestRun, latestMetrics, latestVus] = await Promise.all([
          api.getRun(selectedRunId),
          api.getMetrics(selectedRunId),
          api.getVirtualUsers(selectedRunId),
        ]);
        if (!active) return;
        setRun(latestRun);
        setMetrics(latestMetrics);
        setVus(latestVus);
        if (!isTerminal(latestRun.status)) {
          timer = window.setTimeout(poll, 1000);
        } else {
          loadTestAndRuns();
        }
      } catch {
        // Stop polling on error; the user can re-select the run to retry.
      }
    };

    poll();
    return () => {
      active = false;
      if (timer) window.clearTimeout(timer);
    };
  }, [selectedRunId, loadTestAndRuns]);

  const handleStart = async () => {
    if (!id) return;
    setActionError(null);
    try {
      const started = await api.startRun(id);
      setRuns((prev) => [started, ...prev]);
      setRun(started);
      setMetrics([]);
      setVus([]);
      setSelectedRunId(started.id);
    } catch (e) {
      setActionError(e instanceof ApiError ? e.message : 'Failed to start run.');
    }
  };

  const handleStop = async () => {
    if (!selectedRunId) return;
    setActionError(null);
    try {
      await api.stopRun(selectedRunId);
    } catch (e) {
      setActionError(e instanceof ApiError ? e.message : 'Failed to stop run.');
    }
  };

  if (loadError) {
    return (
      <main className="mx-auto max-w-4xl p-6">
        <p role="alert" className="text-red-700">{loadError}</p>
        <NavLink to="/" className="text-indigo-600 underline">Back to tests</NavLink>
      </main>
    );
  }

  if (!test) {
    return <p role="status" className="p-6 text-gray-600">Loading test…</p>;
  }

  const latest = metrics.at(-1);
  const summary = run?.summary;
  const completed = summary?.totalCompleted ?? latest?.completedCount ?? 0;
  const dropped = summary?.totalDropped ?? latest?.droppedCount ?? 0;
  const succeeded = (summary?.outcomeCounts ?? latest?.outcomeCounts)?.SUCCESS_2XX ?? 0;
  const failed = sumFailed(summary?.outcomeCounts ?? latest?.outcomeCounts);
  const activeVus = run && !isTerminal(run.status) ? latest?.activeRequests ?? 0 : 0;
  const rps = summary?.actualRps ?? latest?.completedRps ?? 0;

  return (
    <main className="mx-auto max-w-4xl p-6">
      <NavLink to="/" className="text-sm text-indigo-600 underline">← Back to tests</NavLink>

      <header className="mt-2 mb-4">
        <h1 className="text-2xl font-semibold text-gray-900">{test.testName}</h1>
        <p className="text-sm text-gray-600">{test.httpMethod} {test.targetUrl}</p>
        <p className="text-sm text-gray-500">
          {test.workloadProfile.maxConcurrency} virtual users ·{' '}
          {test.workloadProfile.stages.reduce((sum, s) => sum + s.durationSeconds, 0)}s
        </p>
      </header>

      {canOperate ? (
        <div className="mb-4 flex gap-3">
          <button
            onClick={handleStart}
            className="rounded bg-indigo-600 px-3 py-2 font-medium text-white hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-indigo-500"
          >
            Start run
          </button>
          {run && run.status === 'RUNNING' && (
            <button
              onClick={handleStop}
              className="rounded bg-amber-600 px-3 py-2 font-medium text-white hover:bg-amber-700 focus:outline-none focus:ring-2 focus:ring-amber-500"
            >
              Stop run
            </button>
          )}
        </div>
      ) : (
        <p className="mb-4 text-sm text-gray-500">Viewers can observe runs but cannot start or stop them.</p>
      )}

      {actionError && <p role="alert" className="mb-4 text-red-700">{actionError}</p>}

      {run && (
        <section aria-live="polite" className="mb-6 rounded border border-gray-200 p-4">
          <div className="mb-3 flex items-center justify-between">
            <h2 className="text-lg font-semibold text-gray-900">Current run</h2>
            <span className={`rounded px-2 py-0.5 text-sm font-medium ${RUN_STATUS_STYLES[run.status]}`}>
              {run.status}
            </span>
          </div>

          <dl className="grid grid-cols-2 gap-x-6 gap-y-2 text-sm sm:grid-cols-3">
            <Stat label="Run ID" value={run.id.slice(0, 8)} />
            <Stat label="Started" value={formatTime(run.startedAt)} />
            <Stat label="Finished" value={formatTime(run.finishedAt)} />
            <Stat label="Configured concurrency" value={String(run.configSnapshot.maxConcurrency)} />
            <Stat label="Active virtual users" value={String(activeVus)} />
            <Stat label="Throughput" value={`${rps.toFixed(1)} req/s`} />
            <Stat label="Completed" value={String(completed)} />
            <Stat label="Succeeded (2xx)" value={String(succeeded)} />
            <Stat label="Failed" value={String(failed)} />
            <Stat label="Dropped" value={String(dropped)} />
            <Stat label="p95 latency" value={formatMs(summary?.latencyStats?.p95Nanos)} />
            <Stat label="Max latency" value={formatMs(summary?.latencyStats?.maxNanos)} />
          </dl>
        </section>
      )}

      {run && (
        <section className="mb-6">
          <h2 className="mb-2 text-lg font-semibold text-gray-900">
            Virtual users <span className="text-sm font-normal text-gray-500">({vus.length})</span>
          </h2>
          {vus.length === 0 ? (
            <p className="text-sm text-gray-600">No virtual users have started yet.</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full border-collapse text-sm">
                <caption className="sr-only">Virtual user activity for the current run</caption>
                <thead>
                  <tr className="border-b border-gray-300 text-left">
                    <th scope="col" className="py-2 pr-4">VU</th>
                    <th scope="col" className="py-2 pr-4">State</th>
                    <th scope="col" className="py-2 pr-4">Requests</th>
                    <th scope="col" className="py-2 pr-4">Last outcome</th>
                    <th scope="col" className="py-2 pr-4">Last activity</th>
                  </tr>
                </thead>
                <tbody>
                  {vus.map((vu) => (
                    <tr key={vu.id} className="border-b border-gray-100">
                      <td className="py-2 pr-4">#{vu.id}</td>
                      <td className="py-2 pr-4">
                        <span className={`rounded px-2 py-0.5 text-xs font-medium ${VU_STATE_STYLES[vu.state]}`}>
                          {vu.state}
                        </span>
                      </td>
                      <td className="py-2 pr-4">{vu.requestCount}</td>
                      <td className="py-2 pr-4">{vu.lastOutcome ?? '—'}</td>
                      <td className="py-2 pr-4">{formatTime(vu.lastActivityAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      )}

      <section>
        <h2 className="mb-2 text-lg font-semibold text-gray-900">Run history</h2>
        {runs.length === 0 ? (
          <p className="text-sm text-gray-600">No runs yet.</p>
        ) : (
          <ul className="flex flex-col gap-2">
            {runs.map((r) => (
              <li key={r.id}>
                <button
                  onClick={() => setSelectedRunId(r.id)}
                  aria-current={r.id === selectedRunId}
                  className={`flex w-full items-center justify-between rounded border p-2 text-left text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 ${r.id === selectedRunId ? 'border-indigo-400 bg-indigo-50' : 'border-gray-200 hover:bg-gray-50'
                    }`}
                >
                  <span>{r.id.slice(0, 8)} · {formatTime(r.createdAt)}</span>
                  <span className={`rounded px-2 py-0.5 text-xs font-medium ${RUN_STATUS_STYLES[r.status]}`}>
                    {r.status}
                  </span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>
    </main>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-gray-500">{label}</dt>
      <dd className="font-medium text-gray-900">{value}</dd>
    </div>
  );
}

export default TestPage;