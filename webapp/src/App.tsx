import { useEffect } from 'react'
import { Route, Routes } from 'react-router-dom'
import Home from './pages/Home'
import TestPage from './pages/TestPage'
import LoginForm from './components/LoginForm'
import useAuth from './context/useAuth'

function App() {
  const { user, initializing, refresh } = useAuth()

  useEffect(() => {
    refresh()
  }, [refresh])

  if (initializing) {
    return (
      <p role="status" className="p-6 text-gray-600">
        Checking session…
      </p>
    )
  }

  if (!user) {
    return <LoginForm />
  }

  return (
    <Routes>
      <Route path="/" element={<Home />} />
      <Route path="/tests/:id" element={<TestPage />} />
    </Routes>
  )
}

export default App

