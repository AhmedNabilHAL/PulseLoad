import { useEffect } from 'react';
import useTests from '../context/useTests';
import TestInfo from './TestInfo';

function TestsList() {
  const { tests, loading, error, fetchTests } = useTests();

  useEffect(() => {
    fetchTests();
  }, [fetchTests]);

  return (
    <section>
      <h2 className="mb-3 text-lg font-semibold text-gray-900">Tests</h2>

      {loading && <p role="status" className="text-gray-600">Loading tests…</p>}
      {error && <p role="alert" className="text-red-700">{error}</p>}
      {!loading && !error && tests.length === 0 && (
        <p className="text-gray-600">No tests yet. Create one to get started.</p>
      )}

      <ul className="flex flex-col gap-2">
        {tests.map((test) => (
          <TestInfo key={test.id} test={test} />
        ))}
      </ul>
    </section>
  );
}

export default TestsList;