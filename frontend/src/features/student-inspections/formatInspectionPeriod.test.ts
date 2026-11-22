import { expect, it } from 'vitest'
import { formatInspectionPeriod } from './formatInspectionPeriod'

it.each([
  ['2026-12-07', '2026-12-11', 'de', 'Mo 07.12. bis Fr 11.12.2026'],
  ['2026-11-18', '2026-11-20', 'de', 'Mi 18.11. bis Fr 20.11.2026'],
  ['2026-12-31', '2027-01-01', 'de', 'Do 31.12.2026 bis Fr 01.01.2027'],
  ['2026-12-07', '2026-12-07', 'de', 'Mo 07.12.2026'],
  ['2026-12-07', '2026-12-11', 'en', 'Mon 7 Dec to Fri 11 Dec 2026'],
  ['2026-12-31', '2027-01-01', 'en', 'Thu 31 Dec 2026 to Fri 1 Jan 2027'],
  ['2026-12-07', '2026-12-07', 'en', 'Mon 7 Dec 2026'],
])('formats %s through %s in %s', (startsOn, endsOn, language, expected) => {
  expect(formatInspectionPeriod(startsOn, endsOn, language)).toBe(expected)
})
