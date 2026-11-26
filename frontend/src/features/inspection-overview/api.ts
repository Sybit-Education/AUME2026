import { api } from '@/lib/auth/client'
import { requireOk } from '@/lib/auth/response'

export const OVERVIEW_STATES = [
  'NONE', 'NO_UPCOMING', 'WAITING_FOR_CARETAKER', 'COUNTER_PROPOSED', 'AGREED', 'ANNOUNCED', 'RUNNING', 'COMPLETED',
] as const
export const PERIOD_KINDS = ['REGULAR'] as const

export type OverviewState = typeof OVERVIEW_STATES[number]
type Kind = typeof PERIOD_KINDS[number] | 'FOLLOW_UP'

interface Page<T> {
  content: T[]
  page: { number: number; size: number; totalElements: number; totalPages: number }
}

export interface RoundView {
  id: number
  startsOn: string
  endsOn: string
  kind: Kind
  state: OverviewState
  controlled: number
  total: number
  passed: number
  failed: number
}

export interface ResidenceRow {
  id: number
  name: string
  lastCompleted: RoundView | null
  current: RoundView | null
  state: OverviewState
}

export interface OpenInspection {
  flat: string
  kind: Kind
  startsOn: string | null
  endsOn: string | null
  outcome: 'OPEN' | 'PASSED' | 'FAILED'
}

export interface ResidenceDetail {
  id: number
  name: string
  rounds: RoundView[]
  openInspections: OpenInspection[]
}

export interface OverviewData {
  rows: Page<ResidenceRow>
  counts: { counterProposed: number; agreedNotAnnounced: number }
}

/** Unbekannte Filterwerte aus der Adresszeile werden ignoriert statt als Fehler gemeldet. */
export function filtersOf(params: URLSearchParams) {
  const pick = <T extends string>(values: readonly T[], value: string | null) => values.find((v) => v === value) ?? null
  return { state: pick(OVERVIEW_STATES, params.get('state')), kind: pick(PERIOD_KINDS, params.get('kind')) }
}

function query(params: URLSearchParams, extra: Record<string, string | null> = {}) {
  const page = Number(params.get('page') ?? '0')
  const next = new URLSearchParams({ page: String(Number.isSafeInteger(page) && page > 0 ? page : 0) })
  Object.entries(extra).forEach(([key, value]) => { if (value) next.set(key, value) })
  return next
}

export async function loadOverview(request: Request): Promise<OverviewData> {
  const params = new URL(request.url).searchParams
  const [rows, counts] = await Promise.all([
    api(`/api/inspection-overview?${query(params, filtersOf(params))}`, { signal: request.signal }),
    api('/api/inspection-overview/counts', { signal: request.signal }),
  ])
  return {
    rows: await requireOk(rows, 'errors.inspectionOverview').json() as OverviewData['rows'],
    counts: await requireOk(counts, 'errors.inspectionOverview').json() as OverviewData['counts'],
  }
}

export async function loadResidenceDetail(request: Request, residenceId: string): Promise<ResidenceDetail> {
  const response = await api(`/api/inspection-overview/${encodeURIComponent(residenceId)}`, { signal: request.signal })
  return await requireOk(response, 'errors.inspectionOverview').json() as ResidenceDetail
}

const DAY: Intl.DateTimeFormatOptions = { day: '2-digit', month: '2-digit', year: 'numeric', timeZone: 'UTC' }

export function formatPeriod(from: string | null, to: string | null, language: string): string {
  if (!from || !to) return '–'
  const format = new Intl.DateTimeFormat(language, DAY)
  return from === to ? format.format(new Date(from)) : format.formatRange(new Date(from), new Date(to))
}

export function formatResult(round: Pick<RoundView, 'passed' | 'failed'>, t: (key: string, values?: object) => string): string {
  return t('inspectionOverview.resultSummary', { passed: round.passed, failed: round.failed })
}

export function formatRoundState(round: RoundView, t: (key: string, values?: object) => string): string {
  if (round.state === 'RUNNING') {
    return t('inspectionOverview.runningProgress', { controlled: round.controlled, total: round.total })
  }
  return t(`inspectionOverview.states.${round.state}`)
}

export const stateChip: Record<OverviewState | OpenInspection['outcome'], string> = {
  NONE: 'wr', NO_UPCOMING: '', WAITING_FOR_CARETAKER: 'wr', COUNTER_PROPOSED: 'wr', AGREED: 'inf',
  ANNOUNCED: 't', RUNNING: 'inf', COMPLETED: 'ok', OPEN: '', PASSED: 'ok', FAILED: 'wr',
}
