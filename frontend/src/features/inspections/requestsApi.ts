import { api } from '@/lib/auth/client'
import { requireOk } from '@/lib/auth/response'
import { UiError, type ErrorKey } from '@/lib/errors'

export type RequestStatus = 'WAITING_FOR_CARETAKER' | 'COUNTER_PROPOSED' | 'AGREED'

/** Ein Kontrollzeitraum, den die Sachbearbeitung der angemeldeten Objektbetreuung vorgeschlagen hat (US-17). */
export interface InspectionRequest {
  id: number
  residenceName: string
  kind: 'REGULAR' | 'INDIVIDUAL'
  status: RequestStatus
  startsOn: string
  endsOn: string
  windowStart: string | null
  windowEnd: string | null
  flatCount: number
  /** Nur bei individuellen Kontrollen (US-18b): die ausgewählten WGs. */
  flatLabels: string[] | null
  counter: { startsOn: string; endsOn: string; windowStart: string | null; windowEnd: string | null; reason: string } | null
  version: number
}

export interface RequestPage {
  content: InspectionRequest[]
  page: { number: number; size: number; totalElements: number; totalPages: number }
}

export interface CounterInput {
  startsOn: string | null
  endsOn: string | null
  windowStart: string | null
  windowEnd: string | null
  reason: string | null
}

/** Längste Begründung, die das Backend annimmt. */
export const MAX_REASON_LENGTH = 2000

const answerErrors: Record<string, ErrorKey> = {
  dates_required: 'errors.periodDatesRequired',
  end_before_start: 'errors.periodEndBeforeStart',
  start_not_in_future: 'errors.periodStartNotFuture',
  invalid_time_window: 'errors.periodTimeWindow',
  reason_required: 'errors.requestReasonRequired',
  reason_too_long: 'errors.requestReasonTooLong',
  period_changed: 'errors.periodChanged',
  period_announced: 'errors.requestAnswered',
  invalid_period_status: 'errors.requestAnswered',
}

export async function listRequests(signal: AbortSignal): Promise<RequestPage> {
  const response = requireOk(await api('/api/inspection-periods/assigned?size=100', { signal }), 'errors.inspectionRequests')
  return (await response.json()) as RequestPage
}

async function send(path: string, body: unknown, signal: AbortSignal, errors: Record<string, ErrorKey>): Promise<void> {
  const response = await api(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
    signal,
  })
  if (response.status === 404) throw new UiError('errors.requestGone')
  if (response.status === 400 || response.status === 409) {
    const error = (await response.json().catch(() => null)) as { code?: string } | null
    throw new UiError(errors[error?.code ?? ''] ?? 'errors.inspectionRequestSave')
  }
  requireOk(response, 'errors.inspectionRequestSave')
}

/** Zustimmen geht nur, solange der Zeitraum noch nicht begonnen hat. Danach bleibt der Gegenvorschlag. */
export function agreeToRequest(id: number, version: number, signal: AbortSignal): Promise<void> {
  return send(`/api/inspection-periods/${id}/agree`, { version }, signal,
    { ...answerErrors, start_not_in_future: 'errors.requestStartPassed' })
}

/** Ohne eigene Uhrzeit übernimmt das Backend die Uhrzeit aus dem Vorschlag. */
export function counterRequest(id: number, input: CounterInput, version: number, signal: AbortSignal): Promise<void> {
  return send(`/api/inspection-periods/${id}/counter`, { ...input, version }, signal, answerErrors)
}
