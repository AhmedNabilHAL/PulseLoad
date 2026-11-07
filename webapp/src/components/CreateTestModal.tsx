import Modal from './layout/Modal';
import CreateTestForm from './CreateTestForm';
import useModal from '../hooks/useModal';

function CreateTestModal() {
  const { isModalOpen, openModal, closeModal } = useModal();
  return (
    <>
      <button onClick={openModal}>
        Create Test
      </button>

      {isModalOpen && (<Modal
        onClose={closeModal}
        modalTitle={''}
        actionBar={undefined}>
        <CreateTestForm />
      </Modal>)}
    </>
  );
}

export default CreateTestModal;