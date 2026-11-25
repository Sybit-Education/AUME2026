import { api, type LoggedInUser } from '@/lib/auth/client'
import { requireOk } from '@/lib/auth/response'

export type UpcomingAppointment = {
  id: number
  startsOn: string
  endsOn: string
  appointmentChanged: boolean
}

export async function loadAppointment(user: LoggedInUser, signal: AbortSignal): Promise<UpcomingAppointment | null> {
  if (!user.roles.includes('STUDENT')) return null
  const response = await api('/api/student/inspection-appointment', { signal })
  signal.throwIfAborted()
  requireOk(response, 'errors.studentInspectionAppointment')
  return response.status === 204 ? null : await response.json() as UpcomingAppointment
}

/** Kriterium mit Bereich, wie es die Schnittstelle von Team 3 liefert (Namen auf Deutsch). */
export type ResultDeficiency = { area: string; criterion: string; note: string | null; buildingCauseSuspected: boolean }
export type ResultResolvedOnSite = { area: string; criterion: string; note: string | null }
export type ResultPassedCriterion = { area: string; criterion: string }

/** US-38: Neuestes veröffentlichtes Ergebnis der eigenen WG (GET /api/student/inspection-results/latest). */
export type StudentResult = {
  inspectionId: number
  kind: 'INITIAL' | 'FOLLOW_UP'
  round: number
  inspectedAt: string | null
  overallResult: 'PASSED' | 'FAILED' | null
  deficiencies: ResultDeficiency[]
  resolvedOnSite: ResultResolvedOnSite[]
  passed: ResultPassedCriterion[]
}

/** `null`, wenn es noch kein sichtbares Ergebnis gibt (204) oder keine aktive WG (404). */
export async function loadLatestResult(signal: AbortSignal): Promise<StudentResult | null> {
  const response = await api('/api/student/inspection-results/latest', { signal })
  signal.throwIfAborted()
  if (response.status === 204 || response.status === 404) return null
  requireOk(response, 'errors.studentInspectionResult')
  return await response.json() as StudentResult
}
