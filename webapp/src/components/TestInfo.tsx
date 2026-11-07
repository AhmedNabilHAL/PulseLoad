import type { Test } from '../types';
import { NavLink } from 'react-router-dom';

function TestInfo({ test }: { test: Test }) {
  return (
    <NavLink
      to={`/tests/${test.id}`}
    >
      <li>
        <strong>{test.testName}</strong> - {test.targetUrl} - {test.httpMethod} - {test.durationInSeconds}s - {test.requestsPerSec} req/s
      </li>
    </NavLink>

  );
}

export default TestInfo;