import { describe, expect, it } from 'vitest'
import type { PreviousDefect, PreviousInspection } from './api'
import { criterionKey, earlierDefects } from './previous'

const defect = (area: string, criterionTitle: string, rating: PreviousDefect['rating'] = 'DEFICIENT'): PreviousDefect =>
  ({ area, criterionTitle, rating, severity: rating === 'DEFICIENT' ? 'LIGHT' : null, note: null, buildingCauseSuspected: false })
const inspection = (id: number, inspectedAt: string, defects: PreviousDefect[]): PreviousInspection =>
  ({ id, inspectedAt, round: 0, overallResult: defects.length ? 'FAILED' : 'PASSED', defects })

describe('previous findings', () => {
  it('keeps the most recent earlier defect per criterion', () => {
    const result = earlierDefects([
      inspection(3, '2026-11-01T09:00:00Z', [defect('Bad', 'Dusche & WC', 'RESOLVED_ON_SITE')]),
      inspection(2, '2026-10-01T09:00:00Z', [defect('Bad', 'Dusche & WC'), defect('Küche', 'Spüle')]),
    ])
    expect(result.size).toBe(2)
    expect(result.get(criterionKey('Bad', 'Dusche & WC'))).toEqual({
      defect: defect('Bad', 'Dusche & WC', 'RESOLVED_ON_SITE'), inspectedAt: '2026-11-01T09:00:00Z',
    })
    expect(result.get(criterionKey('Küche', 'Spüle'))?.inspectedAt).toBe('2026-10-01T09:00:00Z')
  })

  it('matches criteria across checklist versions by area and title, not by id', () => {
    const result = earlierDefects([inspection(1, '2026-10-01T09:00:00Z', [defect('Küche', 'Herd & Backofen')])])
    expect(result.has(criterionKey(' küche ', 'HERD & BACKOFEN'))).toBe(true)
    expect(result.has(criterionKey('Bad', 'Herd & Backofen'))).toBe(false)
  })

  it('is empty without earlier defects', () => {
    expect(earlierDefects([]).size).toBe(0)
    expect(earlierDefects([inspection(1, '2026-10-01T09:00:00Z', [])]).size).toBe(0)
  })
})
