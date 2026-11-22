import { describe, expect, it } from 'vitest'
import { hasFilter, historyQuery, invalidRange, readHistoryFilter, toggleSort, withFilter } from './history'

const read = (query: string) => readHistoryFilter(new URLSearchParams(query))

describe('inspection history filter', () => {
  it('keeps valid filters and passes only those to the backend', () => {
    const filter = read('flat=%204.12%20&result=FAILED&followUp=true&from=2026-01-01&to=2026-02-28&sort=flat&dir=desc&page=2')
    expect(filter).toEqual({
      flat: '4.12', result: 'FAILED', followUp: 'true', from: '2026-01-01', to: '2026-02-28', sort: 'flat', dir: 'desc', page: 2,
    })
    expect(historyQuery(filter).toString()).toBe(
      'page=2&size=20&flat=4.12&result=FAILED&followUp=true&from=2026-01-01&to=2026-02-28&orderBy=FLAT&direction=DESC')
    expect(hasFilter(filter)).toBe(true)
  })

  it('drops invalid values instead of sending them', () => {
    const filter = read('result=MAYBE&followUp=yes&from=2026-02-30&to=gestern&sort=password&dir=up&page=-1')
    expect(filter).toEqual({ flat: '', result: '', followUp: '', from: '', to: '', sort: 'date', dir: 'desc', page: 0 })
    expect(historyQuery(filter).toString()).toBe('page=0&size=20&orderBy=DATE&direction=DESC')
    expect(hasFilter(filter)).toBe(false)
  })

  it('uses the default direction of a column when none is given', () => {
    expect(read('sort=flat').dir).toBe('asc')
    expect(read('').dir).toBe('desc')
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

describe('inspection history table header', () => {
  const params = (query: string) => new URLSearchParams(query)
  const toggle = (query: string, column: Parameters<typeof toggleSort>[2]) =>
    toggleSort(params(query), read(query), column).toString()

  it('starts a new column with its default direction and resets the page', () => {
    expect(toggle('page=3&flat=S', 'flat')).toBe('flat=S&sort=flat&dir=asc')
    expect(toggle('', 'date')).toBe('sort=date&dir=asc')
  })

  it('flips the direction when the same column is clicked again', () => {
    expect(toggle('sort=flat&dir=asc', 'flat')).toBe('sort=flat&dir=desc')
    expect(toggle('sort=flat&dir=desc', 'flat')).toBe('sort=flat&dir=asc')
  })

  it('drops the sort parameters for the default order', () => {
    expect(toggle('sort=date&dir=asc', 'date')).toBe('')
  })

  it('sets and removes filters and resets the page', () => {
    expect(withFilter(params('page=2&sort=flat'), { flat: ' S-01 ' }).toString()).toBe('sort=flat&flat=S-01')
    expect(withFilter(params('flat=S-01&result=FAILED'), { flat: '' }).toString()).toBe('result=FAILED')
    expect(withFilter(params(''), { from: '2026-01-01', to: '' }).toString()).toBe('from=2026-01-01')
  })
})
