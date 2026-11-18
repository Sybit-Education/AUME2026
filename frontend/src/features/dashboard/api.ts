import { api, type LoggedInUser } from '@/lib/auth/client'
import { requireOk } from '@/lib/auth/response'

export async function loadDashboard(user: LoggedInUser, signal: AbortSignal) {
  if (!user.privileges.includes('GREETING_READ')) return { greeting: null }
  const response = requireOk(await api('/api/greeting', { signal }), 'errors.greeting')
  return { greeting: await response.text() }
}
