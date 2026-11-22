import { expect, it } from 'vitest'
import { createInspectionCalendar } from './calendar'

const generatedAt = new Date('2026-11-22T12:34:56.789Z')

it('exports a whole-day period including its last day across a year boundary', () => {
  const contents = createInspectionCalendar(42, '2026-12-30', '2026-12-31', generatedAt)

  expect(contents.split('\r\n')).toEqual([
    'BEGIN:VCALENDAR',
    'VERSION:2.0',
    'PRODID:-//Seezeit//WG Inspection//EN',
    'BEGIN:VEVENT',
    'UID:inspection-42@seezeit',
    'DTSTAMP:20261122T123456Z',
    'DTSTART;VALUE=DATE:20261230',
    'DTEND;VALUE=DATE:20270101',
    'SUMMARY:Seezeit',
    'END:VEVENT',
    'END:VCALENDAR',
    '',
  ])
})

it('exports a one-day period without shifting the start date', () => {
  const contents = createInspectionCalendar(42, '2026-03-29', '2026-03-29', generatedAt)

  expect(contents).toContain('DTSTART;VALUE=DATE:20260329\r\nDTEND;VALUE=DATE:20260330')
})
