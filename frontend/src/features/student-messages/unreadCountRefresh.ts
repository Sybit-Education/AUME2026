import type { LoggedInUser } from '@/lib/auth/client'

type Listener = (user: LoggedInUser) => void
const listeners = new Set<Listener>()

export function subscribeUnreadCountRefresh(listener: Listener) {
  listeners.add(listener)
  return () => { listeners.delete(listener) }
}

export function requestUnreadCountRefresh(user: LoggedInUser) {
  listeners.forEach((listener) => listener(user))
}
