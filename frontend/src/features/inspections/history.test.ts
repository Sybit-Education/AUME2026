import { describe, expect, it } from 'vitest'
import { hasFilter, historyQuery, invalidRange, readHistoryFilter } from './history'

const read = (query: string) => readHistoryFilter(new URLSearchParams(query))

describe('inspection history filter', () => {
  it('keeps valid filters and passes only those to the backend', () => {
    const filter = read('flat=%204.12%20&result=FAILED&followUp=true&from=2026-01-01&to=2026-02-28&page=2')
    expect(filter).toEqual({ flat: '4.12', result: 'FAILED', followUp: 'true', from: '2026-01-01', to: '2026-02-28', page: 2 })
    expect(historyQuery(filter).toString())
      .toBe('page=2&size=20&flat=4.12&result=FAILED&followUp=true&from=2026-01-01&to=2026-02-28')
    expect(hasFilter(filter)).toBe(true)
  })

  it('drops invalid values instead of sending them', () => {
    const filter = read('result=MAYBE&followUp=yes&from=2026-02-30&to=gestern&page=-1')
    expect(filter).toEqual({ flat: '', result: '', followUp: '', from: '', to: '', page: 0 })
    expect(historyQuery(filter).toString()).toBe('page=0&size=20')
    expect(hasFilter(filter)).toBe(false)
  })

  it('limits the flat filter to the backend maximum', () => {
    expect(read(`flat=${'x'.repeat(100)}`).flat).toHaveLength(80)
  })

  it('detects a range whose start lies after its end', () => {
    expect(invalidRange(read('from=2026-03-02&to=2026-03-01'))).toBe(true)
    expect(invalidRange(read('from=2026-03-01&to=2026-03-01'))).toBe(false)
    expect(invalidRange(read('from=2026-03-02'))).toBe(false)
  })
})
