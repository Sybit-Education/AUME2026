import type { PropsWithChildren } from 'react'
import { Navigate } from 'react-router'
import { useAuth } from '../auth/AuthContext'

export default function RequirePrivilege({ privilege, children }: PropsWithChildren<{ privilege: string }>) {
  const { user } = useAuth()
  return user?.privileges.includes(privilege) ? children : <Navigate replace to="/forbidden" />
}
