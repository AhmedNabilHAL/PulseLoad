import CreateTestModal from '../components/CreateTestModal';
import TestsList from '../components/TestsList';
import useAuth from '../context/useAuth';
import { hasRole } from '../types';

function Home() {
  const { user } = useAuth();
  const canCreate = hasRole(user, 'OPERATOR', 'ADMIN');

  return (
    <main className="mx-auto max-w-3xl p-6">
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-2xl font-semibold text-gray-900">Load tests</h1>
        {canCreate && <CreateTestModal />}
      </div>

      <TestsList />

      {!canCreate && (
        <p className="mt-4 text-sm text-gray-500">
          You are signed in as a viewer. Creating and running tests requires the operator role.
        </p>
      )}
    </main>
  );
}

export default Home;