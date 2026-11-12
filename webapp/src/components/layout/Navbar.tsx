import { NavLink } from 'react-router-dom';
import useAuth from '../../context/useAuth';

function Navbar() {
  const { user, logout } = useAuth();

  return (
    <nav className="sticky top-0 z-10 flex items-center justify-between bg-gray-800 p-4 text-white">
      <NavLink to="/" className="font-semibold">
        PulseLoad
      </NavLink>

      {user && (
        <div className="flex items-center gap-3 text-sm">
          <span>
            {user.username}{' '}
            <span className="text-gray-400">
              ({user.roles.map((role) => role.replace('ROLE_', '')).join(', ')})
            </span>
          </span>
          <button
            onClick={() => logout()}
            className="rounded bg-gray-700 px-2 py-1 hover:bg-gray-600 focus:outline-none focus:ring-2 focus:ring-indigo-400"
          >
            Log out
          </button>
        </div>
      )}
    </nav>
  );
}

export default Navbar;
