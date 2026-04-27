import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import Sidebar from './components/Sidebar'
import Dashboard from './pages/Dashboard'
import UserManagement from './pages/UserManagement'
import UserProfile from './pages/UserProfile'
import Revenue from './pages/Revenue'
import Login from './pages/Login'

function RequireAuth({ children }: { children: JSX.Element }) {
  const token = localStorage.getItem('admin_token')
  const role  = localStorage.getItem('admin_role')
  if (!token || role !== 'ADMIN') return <Navigate to="/login" replace />
  return children
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/*" element={
          <RequireAuth>
            <div className="flex h-screen overflow-hidden">
              <Sidebar />
              <main className="flex-1 overflow-y-auto bg-[#F0F2F8]">
                <Routes>
                  <Route path="/"        element={<Dashboard />} />
                  <Route path="/users"   element={<UserManagement />} />
                  <Route path="/user/:id" element={<UserProfile />} />
                  <Route path="/revenue" element={<Revenue />} />
                  <Route path="*"        element={<Navigate to="/" replace />} />
                </Routes>
              </main>
            </div>
          </RequireAuth>
        } />
      </Routes>
    </BrowserRouter>
  )
}
