import { describe, expect, it } from 'vitest'
import type { Criterion, Finding } from './api'
import { areas, suggestedResult } from './summary'

const criterion = (id: number, area: string, position = id): Criterion =>
  ({ id, area, title: `K${id}`, description: '', position })
const finding = (criterionId: number, rating: Finding['rating']): Finding =>
  ({ criterionId, rating, severity: rating === 'DEFICIENT' ? 'LIGHT' : null, note: null, buildingCauseSuspected: false })

describe('inspection summary', () => {
  it('groups criteria by area in checklist order and counts open and deficient criteria', () => {
    const result = areas(
      [criterion(3, 'Bad', 3), criterion(1, 'Küche', 1), criterion(2, 'Küche', 2)],
      [finding(1, 'DEFICIENT'), finding(2, 'RESOLVED_ON_SITE')],
    )
    expect(result.map((area) => area.area)).toEqual(['Küche', 'Bad'])
    expect(result[0]).toMatchObject({ open: 0, deficient: 1 })
    expect(result[1]).toMatchObject({ open: 1, deficient: 0 })
  })

  it('suggests a follow-up only for open deficiencies', () => {
    expect(suggestedResult([finding(1, 'OK'), finding(2, 'RESOLVED_ON_SITE')])).toBe('PASSED')
    expect(suggestedResult([finding(1, 'DEFICIENT')])).toBe('FAILED')
  })
})
