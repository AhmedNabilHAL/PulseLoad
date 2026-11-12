import { NavLink } from 'react-router-dom';
import type { Test } from '../types';

function TestInfo({ test }: { test: Test }) {
  const vus = test.workloadProfile.maxConcurrency;
  const duration = test.workloadProfile.stages.reduce((sum, stage) => sum + stage.durationSeconds, 0);

  return (
    <li>
      <NavLink
        to={`/tests/${test.id}`}
        className="block rounded border border-gray-200 p-3 hover:border-indigo-400 hover:bg-indigo-50 focus:outline-none focus:ring-2 focus:ring-indigo-500"
      >
        <span className="font-medium text-gray-900">{test.testName}</span>
        <span className="block text-sm text-gray-600">
          {test.httpMethod} {test.targetUrl}
        </span>
        <span className="block text-sm text-gray-500">
          {vus} virtual users · {duration}s
        </span>
      </NavLink>
    </li>
  );
}

export default TestInfo;