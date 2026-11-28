import { api, devLogin, initializeAuth, login, logout, type LoggedInUser } from './client'
import { UiError } from '@/lib/errors'

export interface Session {
  ready: boolean
  mode: 'dev' | 'keycloak' | null
  user: LoggedInUser | null
}

let snapshot: Session = { ready: false, mode: null, user: null }
let pending: Promise<Session> | undefined
let revision = 0
const listeners = new Set<() => void>()

export function subscribe(listener: () => void) {
  listeners.add(listener)
  return () => { listeners.delete(listener) }
}

export function getSession() { return snapshot }

function publish(value: Session) {
  snapshot = value
  listeners.forEach((listener) => listener())
}

export function invalidateSession() {
  revision++
  pending = undefined
  publish({ ready: false, mode: null, user: null })
}

export function forgetUser() {
  revision++
  pending = undefined
  publish({ ...snapshot, ready: true, user: null })
}

export function refreshSession(): Promise<Session> {
  if (pending) return pending
  const started = revision
  const task = (async () => {
    const config = await initializeAuth()
    let response: Response
    try {
      response = await api('/api/me')
    } catch (cause) {
      if (!(cause instanceof UiError) || cause.key !== 'errors.sessionExpired') throw cause
      response = new Response(null, { status: 401 })
    }
    if (!response.ok && response.status !== 401) throw new UiError('errors.profile')
    const user = response.status === 401 ? null : (await response.json()) as LoggedInUser
    // An earlier request must not restore a session after logout or a new login.
    if (started === revision) publish({ ready: true, mode: config.mode, user })
    return snapshot
  })()
  pending = task
  void task.finally(() => { if (pending === task) pending = undefined }).catch(() => {})
  return task
}

export function ensureSession(): Promise<Session> {
  return pending ?? (snapshot.ready ? Promise.resolve(snapshot) : refreshSession())
}

export async function signIn(username: string, password: string) {
  await login(username, password)
  revision++
  pending = undefined
  const session = await refreshSession()
  if (!session.user) throw new UiError('errors.sessionExpired')
}

export async function signInDev(userKey: string) {
  await devLogin(userKey)
  revision++
  pending = undefined
  const session = await refreshSession()
  if (!session.user) throw new UiError('errors.sessionExpired')
}

export async function signOut() {
  try {
    await logout()
  } finally {
    forgetUser()
  }
}
