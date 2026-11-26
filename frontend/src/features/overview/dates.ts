import type { UpcomingInspection } from './api'

/** Parse an ISO date as a local date; `new Date('2026-11-18')` would be UTC and can shift the day. */
function parse(date: string) {
  const [year, month, day] = date.split('-').map(Number)
  return new Date(year, month - 1, day)
}

function format(date: string, language: string, withYear: boolean) {
  return new Intl.DateTimeFormat(language, {
    weekday: 'short', day: '2-digit', month: '2-digit', year: withYear ? 'numeric' : undefined,
  }).format(parse(date))
}

/**
 * Dates of an upcoming inspection: `period` for an inspection period, `due` for the deadline
 * of a follow-up inspection, otherwise `null`.
 */
export function inspectionDates(inspection: UpcomingInspection, language: string) {
  if (inspection.startsOn && inspection.endsOn) {
    return {
      type: 'period' as const,
      from: format(inspection.startsOn, language, false),
      to: format(inspection.endsOn, language, true),
    }
  }
  if (inspection.dueOn) return { type: 'due' as const, date: format(inspection.dueOn, language, true) }
  return null
}
