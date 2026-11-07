import ReactDOM from 'react-dom';
import { useEffect } from 'react';

interface ModalPropsType {
  onClose: () => void;
  modalTitle: string;
  children: React.ReactNode | React.ReactNode[];
  actionBar: React.ReactNode;
}

function Modal({ onClose, modalTitle, children, actionBar }: ModalPropsType) {
  useEffect(() => {
    document.body.classList.add('overflow-hidden');

    return () => {
      document.body.classList.remove('overflow-hidden');
    };
  }, []);

  return ReactDOM.createPortal(
    <div>
      <div
        onClick={onClose}
        className="fixed inset-0 bg-gray-300 opacity-80"
      ></div>
      <div className="fixed inset-20 p-10 bg-white overflow-auto">
        <button
          onClick={onClose}
          className="absolute right-4 top-4 rounded-md text-gray-400 hover:bg-gray-100 hover:text-gray-600 focus:outline-none focus:ring-2 focus:ring-indigo-500 p-1 transition-colors"
          aria-label="Close modal"
        >
          <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>

        <h2 className="text-xl font-semibold text-gray-900 mb-2">
          {modalTitle}
        </h2>
        <div className="flex flex-col justify-between h-full text-black">
          {children}
          <div className="flex justify-end">{actionBar}</div>
        </div>
      </div>
    </div>,
    document.querySelector('.modal-container')!
  );
}

export default Modal;
