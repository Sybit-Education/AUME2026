import type { OverallResult } from './api'

export type SortColumn = 'date' | 'flat' | 'kind' | 'result' | 'inspector'
export type SortDirection = 'asc' | 'desc'

export interface HistoryFilter {
  flat: string
  result: OverallResult | ''
  followUp: 'true' | 'false' | ''
  from: string
  to: string
  sort: SortColumn
  dir: SortDirection
  page: number
}

const results: readonly string[] = ['PASSED', 'FAILED']
const columns: readonly string[] = ['date', 'flat', 'kind', 'result', 'inspector']
const isoDate = /^\d{4}-\d{2}-\d{2}$/

/** Neueste zuerst; bei allen anderen Spalten beginnt ein Klick aufsteigend. */
export function defaultDirection(column: SortColumn): SortDirection {
  return column === 'date' ? 'desc' : 'asc'
}

function validDate(value: string | null): string {
  if (!value || !isoDate.test(value)) return ''
  const date = new Date(`${value}T00:00:00Z`)
  return !Number.isNaN(date.getTime()) && date.toISOString().startsWith(value) ? value : ''
}

/** Liest Filter und Sortierung aus der Adresse; ungültige Werte werden verworfen statt ans Backend gegeben. */
export function readHistoryFilter(params: URLSearchParams): HistoryFilter {
  const result = params.get('result') ?? ''
  const followUp = params.get('followUp') ?? ''
  const sortParam = params.get('sort') ?? ''
  const sort = columns.includes(sortParam) ? sortParam as SortColumn : 'date'
  const dirParam = params.get('dir')
  const page = Number(params.get('page') ?? '0')
  return {
    flat: (params.get('flat') ?? '').trim().slice(0, 80),
    result: results.includes(result) ? result as OverallResult : '',
    followUp: followUp === 'true' || followUp === 'false' ? followUp : '',
    from: validDate(params.get('from')),
    to: validDate(params.get('to')),
    sort,
    dir: dirParam === 'asc' || dirParam === 'desc' ? dirParam : defaultDirection(sort),
    page: Number.isSafeInteger(page) && page >= 0 ? page : 0,
  }
}

/** Ein Zeitraum mit „von“ nach „bis“ kann nichts finden und wird über der Tabelle gemeldet. */
export function invalidRange(filter: HistoryFilter): boolean {
  return filter.from !== '' && filter.to !== '' && filter.from > filter.to
}

const orderBy: Record<SortColumn, string> = {
  date: 'DATE', flat: 'FLAT', kind: 'KIND', result: 'RESULT', inspector: 'INSPECTOR',
}

/** Query für das Backend: nur gesetzte Filter, Sortierung, feste Seitengröße. */
export function historyQuery(filter: HistoryFilter): URLSearchParams {
  const query = new URLSearchParams({ page: String(filter.page), size: '20' })
  for (const key of ['flat', 'result', 'followUp', 'from', 'to'] as const) {
    if (filter[key]) query.set(key, filter[key])
  }
  query.set('orderBy', orderBy[filter.sort])
  query.set('direction', filter.dir.toUpperCase())
  return query
}

export function hasFilter(filter: HistoryFilter): boolean {
  return Boolean(filter.flat || filter.result || filter.followUp || filter.from || filter.to)
}

/**
 * Neue Adresse nach einem Klick auf eine Spaltenüberschrift: gleiche Spalte dreht die Richtung,
 * neue Spalte beginnt mit ihrer Standardrichtung. Zurück auf Seite 1.
 */
export function toggleSort(params: URLSearchParams, current: HistoryFilter, column: SortColumn): URLSearchParams {
  const next = new URLSearchParams(params)
  const dir = current.sort === column ? (current.dir === 'asc' ? 'desc' : 'asc') : defaultDirection(column)
  if (column === 'date' && dir === 'desc') {
    next.delete('sort')
    next.delete('dir')
  } else {
    next.set('sort', column)
    next.set('dir', dir)
  }
  next.delete('page')
  return next
}

/** Setzt oder entfernt Filterwerte (leer = entfernen) und springt auf Seite 1. */
export function withFilter(params: URLSearchParams, values: Partial<Record<'flat' | 'result' | 'followUp' | 'from' | 'to', string>>): URLSearchParams {
  const next = new URLSearchParams(params)
  for (const [key, value] of Object.entries(values)) {
    if (value && value.trim()) next.set(key, value.trim())
    else next.delete(key)
  }
  next.delete('page')
  return next
}

/** Datum in der Sprache der Oberfläche, z. B. „23.10.2026“. */
export function formatDate(value: string | null, language: string | undefined): string {
  return value ? new Date(value).toLocaleDateString(language, { day: '2-digit', month: '2-digit', year: 'numeric' }) : '–'
}
