import { api, type LoggedInUser } from '@/lib/auth/client'
import { requireOk } from '@/lib/auth/response'

type UpcomingAppointment = {
  id: number
  startsOn: string
  endsOn: string
}

export async function loadAppointment(user: LoggedInUser, signal: AbortSignal): Promise<UpcomingAppointment | null> {
  if (!user.roles.includes('STUDENT')) return null
  const response = await api('/api/student/inspection-appointment', { signal })
  signal.throwIfAborted()
  requireOk(response, 'errors.studentInspectionAppointment')
  return response.status === 204 ? null : await response.json() as UpcomingAppointment
}
