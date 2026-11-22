import { api } from '@/lib/auth/client'
import { requireOk } from '@/lib/auth/response'
import { UiError, type ErrorKey } from '@/lib/errors'

export type PeriodStatus = 'WAITING_FOR_CARETAKER' | 'COUNTER_PROPOSED' | 'AGREED'

export interface StaffMember {
  id: number
  name: string
}

export interface ResidenceOption {
  id: number
  name: string
  flatCount: number
  caretakerId: number | null
  flatsWithOpenInspection: string[]
}

export interface PlanningOptions {
  residences: ResidenceOption[]
  caretakers: StaffMember[]
}

export interface InspectionPeriod {
  id: number
  residenceId: number
  residenceName: string
  kind: 'REGULAR'
  status: PeriodStatus
  announcedAt: string | null
  startsOn: string
  endsOn: string
  windowStart: string | null
  windowEnd: string | null
  caretaker: StaffMember | null
  flatCount: number
  counter: { startsOn: string; endsOn: string; windowStart: string | null; windowEnd: string | null; reason: string } | null
  version: number
}

export interface PeriodPage {
  content: InspectionPeriod[]
  page: { number: number; size: number; totalElements: number; totalPages: number }
}

export interface PlanningData {
  options: PlanningOptions
  periods: PeriodPage
}

export interface PeriodInput {
  startsOn: string | null
  endsOn: string | null
  windowStart: string | null
  windowEnd: string | null
  caretakerId: number | null
}

const saveErrors: Record<string, ErrorKey> = {
  dates_required: 'errors.periodDatesRequired',
  end_before_start: 'errors.periodEndBeforeStart',
  start_not_in_future: 'errors.periodStartNotFuture',
  invalid_time_window: 'errors.periodTimeWindow',
  caretaker_required: 'errors.caretakerRequired',
  unknown_caretaker: 'errors.unknownCaretaker',
  no_flats_available: 'errors.periodNoFlats',
  period_announced: 'errors.periodAnnounced',
  period_changed: 'errors.periodChanged',
  invalid_period_status: 'errors.periodInvalidStatus',
}

export async function getPlanningOptions(signal: AbortSignal): Promise<PlanningOptions> {
  const response = requireOk(await api('/api/inspection-periods/options', { signal }), 'errors.inspectionPlanning')
  return (await response.json()) as PlanningOptions
}

export async function listPeriods(page: number, signal: AbortSignal): Promise<PeriodPage> {
  const response = requireOk(await api(`/api/inspection-periods?page=${page}&size=20`, { signal }), 'errors.inspectionPlanning')
  return (await response.json()) as PeriodPage
}

async function send(path: string, method: 'POST' | 'PUT', body: unknown, signal: AbortSignal): Promise<void> {
  const response = await api(path, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
    signal,
  })
  if (response.status === 400 || response.status === 409) {
    const error = (await response.json().catch(() => null)) as { code?: string } | null
    throw new UiError(saveErrors[error?.code ?? ''] ?? 'errors.inspectionPlanningSave')
  }
  requireOk(response, 'errors.inspectionPlanningSave')
}

export function createPeriod(residenceId: number, input: PeriodInput, signal: AbortSignal): Promise<void> {
  return send('/api/inspection-periods', 'POST', { residenceId, ...input }, signal)
}

export function updatePeriod(id: number, input: PeriodInput, version: number, signal: AbortSignal): Promise<void> {
  return send(`/api/inspection-periods/${id}`, 'PUT', { ...input, version }, signal)
}

export function acceptCounter(id: number, version: number, signal: AbortSignal): Promise<void> {
  return send(`/api/inspection-periods/${id}/accept-counter`, 'POST', { version }, signal)
}
