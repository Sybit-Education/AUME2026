import { describe, expect, it } from 'vitest'
import type { UpcomingInspection } from './api'
import { inspectionDates } from './dates'

const inspection = (dates: Partial<UpcomingInspection>): UpcomingInspection => ({
  id: 1, flatLabel: '4.12', residenceName: 'Sonne', kind: 'INITIAL', round: 0, state: 'ANNOUNCED',
  periodId: null, canRespond: false, startsOn: null, endsOn: null, dueOn: null, ...dates,
})

describe('inspection dates', () => {
  it('formats a period with weekdays and the year only at its end', () => {
    expect(inspectionDates(inspection({ startsOn: '2026-11-18', endsOn: '2026-11-20' }), 'de'))
      .toEqual({ type: 'period', from: 'Mi., 18.11.', to: 'Fr., 20.11.2026' })
  })

  it('shows the deadline of a follow-up inspection', () => {
    expect(inspectionDates(inspection({ kind: 'FOLLOW_UP', round: 1, dueOn: '2026-11-24' }), 'en'))
      .toEqual({ type: 'due', date: 'Tue, 11/24/2026' })
  })

  it('returns null without a period or deadline', () => {
    expect(inspectionDates(inspection({}), 'de')).toBeNull()
  })
})
