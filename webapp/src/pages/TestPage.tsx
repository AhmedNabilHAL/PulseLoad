import { useParams } from 'react-router-dom';

function TestPage() {
  const { id } = useParams<{ id: string }>();

  return (
    <div>
      <h2>Test Page for ID: {id}</h2>
    </div>
  );
}

export default TestPage;