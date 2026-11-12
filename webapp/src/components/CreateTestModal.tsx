import Modal from './layout/Modal';
import CreateTestForm from './CreateTestForm';
import useModal from '../hooks/useModal';

function CreateTestModal() {
  const { isModalOpen, openModal, closeModal } = useModal();
  return (
    <>
      <button
        onClick={openModal}
        className="rounded bg-indigo-600 px-3 py-2 font-medium text-white hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-indigo-500"
      >
        Create test
      </button>

      {isModalOpen && (
        <Modal onClose={closeModal} modalTitle="Create load test" actionBar={undefined}>
          <CreateTestForm onCreated={closeModal} />
        </Modal>
      )}
    </>
  );
}

export default CreateTestModal;