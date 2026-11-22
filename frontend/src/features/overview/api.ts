import { api } from '@/lib/auth/client'
import { requireOk } from '@/lib/auth/response'

export type OverviewState = 'PROPOSED' | 'COUNTER_PROPOSED' | 'AGREED' | 'ANNOUNCED' | 'PLANNED' | 'IN_PROGRESS'
export type InspectionKind = 'INITIAL' | 'FOLLOW_UP'

/** Dates as ISO dates (`2026-11-18`). Period for initial inspections, deadline for follow-ups. */
export interface UpcomingInspection {
  id: number
  flatLabel: string
  residenceName: string
  kind: InspectionKind
  round: number
  state: OverviewState
  /** Inspection period the inspection belongs to; `null` for follow-ups. */
  periodId: number | null
  /** The signed-in caretaker has to answer this proposal (deputies cannot). */
  canRespond: boolean
  startsOn: string | null
  endsOn: string | null
  dueOn: string | null
}

export interface CaretakerOverview {
  proposals: number
  announced: number
  followUps: number
  /** `null` until damage reports exist (US-14, #12). */
  openDamages: number | null
  upcoming: UpcomingInspection[]
}

export async function loadCaretakerOverview(signal: AbortSignal): Promise<CaretakerOverview> {
  const response = requireOk(await api('/api/overview/caretaker', { signal }), 'errors.overview')
  return (await response.json()) as CaretakerOverview
}
