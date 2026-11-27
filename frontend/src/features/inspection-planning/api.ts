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
  /** Auswahl für individuelle Kontrollen (US-18b). */
  flats: { id: number; label: string }[]
}

export type PeriodKind = 'REGULAR' | 'INDIVIDUAL'
export const OCCASIONS = ['SAMPLE', 'COMPLAINT', 'OTHER'] as const
export type Occasion = typeof OCCASIONS[number]

export interface PlanningOptions {
  residences: ResidenceOption[]
  caretakers: StaffMember[]
}

export interface InspectionPeriod {
  id: number
  residenceId: number
  residenceName: string
  kind: PeriodKind
  occasion: Occasion | null
  status: PeriodStatus
  announcedAt: string | null
  /** Gesetzt, sobald ein angekündigter Termin geändert wurde (US-18). */
  rescheduledAt: string | null
  startsOn: string
  endsOn: string
  windowStart: string | null
  windowEnd: string | null
  caretaker: StaffMember | null
  flatCount: number
  /** Nur bei individuellen Kontrollen gesetzt. */
  flatLabels: string[] | null
  counter: { startsOn: string; endsOn: string; windowStart: string | null; windowEnd: string | null; reason: string } | null
  version: number
}

export interface PeriodPage {
  content: InspectionPeriod[]
  page: { number: number; size: number; totalElements: number; totalPages: number }
}

export type Channel = 'IN_APP'

export interface AnnouncementDraft {
  periodId: number
  residenceName: string
  kind: PeriodKind
  flatCount: number
  flatLabels: string[] | null
  channels: Channel[]
  message: string
}

export interface PlanningData {
  options: PlanningOptions
  periods: PeriodPage
  /** Nur geladen, wenn der Ankündigen-Dialog offen ist. */
  draft?: AnnouncementDraft
}

export interface AnnouncementInput {
  message: string
  attachmentId: number | null
  attachmentName: string | null
}

export interface PeriodInput {
  startsOn: string | null
  endsOn: string | null
  windowStart: string | null
  windowEnd: string | null
  caretakerId: number | null
}

/** Nur beim Anlegen einer individuellen Kontrolle; ohne `kind` legt das Backend eine reguläre an. */
export interface NewPeriodInput extends PeriodInput {
  kind?: 'INDIVIDUAL'
  flatIds?: number[]
  occasion?: Occasion | null
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
  period_started: 'errors.periodStarted',
  message_required: 'errors.announceMessageRequired',
  message_too_long: 'errors.announceMessageTooLong',
  unknown_attachment: 'errors.announceAttachment',
  period_unchanged: 'errors.periodUnchanged',
  occasion_required: 'errors.periodOccasionRequired',
  flats_required: 'errors.periodFlatsRequired',
  unknown_flat: 'errors.periodUnknownFlat',
  flat_has_open_inspection: 'errors.periodFlatOpen',
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

export function createPeriod(residenceId: number, input: NewPeriodInput, signal: AbortSignal): Promise<void> {
  return send('/api/inspection-periods', 'POST', { residenceId, ...input }, signal)
}

export function updatePeriod(id: number, input: PeriodInput, version: number, signal: AbortSignal): Promise<void> {
  return send(`/api/inspection-periods/${id}`, 'PUT', { ...input, version }, signal)
}

export function acceptCounter(id: number, version: number, signal: AbortSignal): Promise<void> {
  return send(`/api/inspection-periods/${id}/accept-counter`, 'POST', { version }, signal)
}

export function withdrawPeriod(id: number, version: number, signal: AbortSignal): Promise<void> {
  return send(`/api/inspection-periods/${id}/withdraw`, 'POST', { version }, signal)
}

export async function getAnnouncementDraft(id: number, signal: AbortSignal): Promise<AnnouncementDraft> {
  const response = requireOk(await api(`/api/inspection-periods/${id}/announcement-draft`, { signal }), 'errors.inspectionPlanning')
  return (await response.json()) as AnnouncementDraft
}

/** Nutzt den bestehenden Upload aus dem Modul attachment (nur im Profil dev registriert). */
export async function uploadAttachment(file: File, signal: AbortSignal): Promise<number> {
  const body = new FormData()
  body.append('file', file)
  const response = requireOk(await api('/api/attachments', { method: 'POST', body, signal }), 'errors.announceAttachment')
  return ((await response.json()) as { id: number }).id
}

export function announcePeriod(id: number, input: AnnouncementInput, version: number, signal: AbortSignal): Promise<void> {
  return send(`/api/inspection-periods/${id}/announce`, 'POST', { ...input, version }, signal)
}

export function reschedulePeriod(id: number, input: Omit<PeriodInput, 'caretakerId'>, version: number, signal: AbortSignal): Promise<void> {
  return send(`/api/inspection-periods/${id}/reschedule`, 'POST', { ...input, version }, signal)
}
