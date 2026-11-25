import { Navigate, Outlet } from 'react-router'
import { useAuth } from '@/lib/auth/AuthContext'
import { loginDestination } from '@/lib/auth/returnTo'
import LoadingPage from '@/components/LoadingPage'

export default function RequireAuth() {
  const { ready, user, getRequestedPath } = useAuth()
  if (!ready) return <LoadingPage />
  if (!user) {
    const destination = getRequestedPath()
    if (destination.split(/[?#]/)[0] === '/login') return <LoadingPage />
    return <Navigate replace to={loginDestination(destination)} />
  }
  return <Outlet />
}
