
import type { Test } from '../types';
import useTests from '../context/useTests';
import TestInfo from './TestInfo';

function TestsList() {
  const { tests } = useTests();
  return (
    <div>
      <h2>Tests List</h2>
      <ul>
        {tests.map((test) => (
          <TestInfo key={test.id} test={test} />
        ))}
      </ul>
    </div>
  );
}

export default TestsList;