import type { OverallResult } from './api'

export interface HistoryFilter {
  flat: string
  result: OverallResult | ''
  followUp: 'true' | 'false' | ''
  from: string
  to: string
  page: number
}

const results: readonly string[] = ['PASSED', 'FAILED']
const isoDate = /^\d{4}-\d{2}-\d{2}$/

function validDate(value: string | null): string {
  if (!value || !isoDate.test(value)) return ''
  const date = new Date(`${value}T00:00:00Z`)
  return !Number.isNaN(date.getTime()) && date.toISOString().startsWith(value) ? value : ''
}

/** Liest die Filter aus der Adresse; ungültige Werte werden verworfen statt ans Backend gegeben. */
export function readHistoryFilter(params: URLSearchParams): HistoryFilter {
  const result = params.get('result') ?? ''
  const followUp = params.get('followUp') ?? ''
  const page = Number(params.get('page') ?? '0')
  return {
    flat: (params.get('flat') ?? '').trim().slice(0, 80),
    result: results.includes(result) ? result as OverallResult : '',
    followUp: followUp === 'true' || followUp === 'false' ? followUp : '',
    from: validDate(params.get('from')),
    to: validDate(params.get('to')),
    page: Number.isSafeInteger(page) && page >= 0 ? page : 0,
  }
}

/** Ein Zeitraum mit „von“ nach „bis“ kann nichts finden und wird im Formular gemeldet. */
export function invalidRange(filter: HistoryFilter): boolean {
  return filter.from !== '' && filter.to !== '' && filter.from > filter.to
}

/** Query für das Backend: nur gesetzte Filter, feste Seitengröße. */
export function historyQuery(filter: HistoryFilter): URLSearchParams {
  const query = new URLSearchParams({ page: String(filter.page), size: '20' })
  for (const key of ['flat', 'result', 'followUp', 'from', 'to'] as const) {
    if (filter[key]) query.set(key, filter[key])
  }
  return query
}

export function hasFilter(filter: HistoryFilter): boolean {
  return Boolean(filter.flat || filter.result || filter.followUp || filter.from || filter.to)
}

/** Datum in der Sprache der Oberfläche, z. B. „23.10.2026“. */
export function formatDate(value: string | null, language: string | undefined): string {
  return value ? new Date(value).toLocaleDateString(language, { day: '2-digit', month: '2-digit', year: 'numeric' }) : '–'
}
