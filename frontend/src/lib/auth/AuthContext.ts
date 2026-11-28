import { createContext, useContext } from 'react'
import type { Session } from './session'

export interface AuthState extends Session {
  busy: boolean
  getRequestedPath: () => string
  signIn: (username: string, password: string) => Promise<void>
  signInDev: (userKey: string) => Promise<void>
  signOut: () => Promise<void>
}

export const AuthContext = createContext<AuthState | null>(null)

export function useAuth(): AuthState {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth requires AuthProvider')
  return context
}
