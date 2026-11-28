import { useState, useSyncExternalStore, type PropsWithChildren } from 'react'
import { AuthContext } from './AuthContext'
import { getSession, subscribe, signIn, signInDev, signOut } from './session'

export default function AuthProvider({ children, getRequestedPath }: PropsWithChildren<{ getRequestedPath: () => string }>) {
  const session = useSyncExternalStore(subscribe, getSession)
  const [busy, setBusy] = useState(false)

  async function authenticate(username: string, password: string) {
    setBusy(true)
    try { await signIn(username, password) } finally { setBusy(false) }
  }

  async function authenticateDev(userKey: string) {
    setBusy(true)
    try { await signInDev(userKey) } finally { setBusy(false) }
  }

  async function endSession() {
    setBusy(true)
    try { await signOut() } finally { setBusy(false) }
  }

  return (
    <AuthContext value={{ ...session, busy, getRequestedPath, signIn: authenticate, signInDev: authenticateDev, signOut: endSession }}>
      {children}
    </AuthContext>
  )
}
