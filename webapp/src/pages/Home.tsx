import CreateTestModal from '../components/CreateTestModal';
import TestsList from '../components/TestsList';

function Home() {
  return (
    <div>
      <TestsList />
      <CreateTestModal />
    </div>
  )
}

export default Home;