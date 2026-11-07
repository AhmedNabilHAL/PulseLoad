import Navbar from './Navbar';

function DefaultLayout({ children }: { children: React.ReactNode | React.ReactNode[] }) {
  return (
    <div>
      <Navbar />
      {children}
    </div>
  )
}

export default DefaultLayout;