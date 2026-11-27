import { expect, it } from 'vitest'
import type { InspectionSummary } from './api'
import { loadAllPages } from './allPages'
import { statusOf } from './upcomingStatus'

const inspection = (startsOn: string | null, status: InspectionSummary['status'] = 'PLANNED'): InspectionSummary => ({
  id: 1, flatLabel: '4.12', residenceName: 'Sonne', status, round: 0, startsOn, endsOn: startsOn,
  windowStart: null, windowEnd: null, periodKind: 'REGULAR',
})

it('counts an inspection as announced only before its first day', () => {
  expect(statusOf(inspection('2026-11-28'), '2026-11-27')).toBe('ANNOUNCED')
  // Ab dem ersten Tag des Zeitraums darf gestartet werden.
  expect(statusOf(inspection('2026-11-27'), '2026-11-27')).toBe('READY')
  expect(statusOf(inspection('2026-11-26'), '2026-11-27')).toBe('READY')
  expect(statusOf(inspection(null), '2026-11-27')).toBe('READY')
  expect(statusOf(inspection('2026-11-28', 'DRAFT'), '2026-11-27')).toBe('DRAFT')
})

it('loads every page instead of only the first one', async () => {
  const pages = [[1, 2], [3, 4], [5]]
  const loaded = await loadAllPages(async (number) => ({
    content: pages[number],
    page: { number, size: 2, totalElements: 5, totalPages: pages.length },
  }))
  expect(loaded.content).toEqual([1, 2, 3, 4, 5])
  expect(loaded.page.totalPages).toBe(1)
})
