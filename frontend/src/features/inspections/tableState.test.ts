import { describe, expect, it } from 'vitest'
import { compareValues, contains, overlaps } from './tableState'

describe('table sorting', () => {
  it('sorts text, numbers and dates and keeps empty values last in both directions', () => {
    const values = ['S-10', null, 'S-2', 'S-01']
    expect([...values].sort((a, b) => compareValues(a, b, 'asc'))).toEqual(['S-01', 'S-2', 'S-10', null])
    expect([...values].sort((a, b) => compareValues(a, b, 'desc'))).toEqual(['S-10', 'S-2', 'S-01', null])
    expect([3, 1, 2].sort((a, b) => compareValues(a, b, 'asc'))).toEqual([1, 2, 3])
    expect(['2026-12-09', '2026-11-30'].sort((a, b) => compareValues(a, b, 'asc'))).toEqual(['2026-11-30', '2026-12-09'])
  })
})

describe('table filters', () => {
  it('finds text regardless of case and surrounding spaces', () => {
    expect(contains('Sonnenbühl West I', { text: ' west ' })).toBe(true)
    expect(contains('Egg', { text: 'west' })).toBe(false)
    expect(contains('Egg', undefined)).toBe(true)
  })

  it('keeps periods that overlap the chosen days, both days inclusive', () => {
    const december = { from: '2026-12-01', to: '2026-12-05' }
    expect(overlaps('2026-12-05', '2026-12-09', december)).toBe(true)
    expect(overlaps('2026-11-28', '2026-12-01', december)).toBe(true)
    expect(overlaps('2026-12-06', '2026-12-09', december)).toBe(false)
    expect(overlaps('2026-12-03', '2026-12-04', { from: '2026-12-04' })).toBe(true)
    expect(overlaps('2026-12-03', '2026-12-04', { to: '2026-12-02' })).toBe(false)
  })

  it('drops rows without a period only while a period filter is set', () => {
    expect(overlaps(null, null, { from: '2026-12-01' })).toBe(false)
    expect(overlaps(null, null, undefined)).toBe(true)
    expect(overlaps(null, null, { from: '', to: '' })).toBe(true)
  })
})
