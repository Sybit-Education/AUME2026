// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import i18n from '@/lib/i18n'
import InspectionAppointment from './InspectionAppointment'

const appointment = { id: 42, startsOn: '2026-12-28', endsOn: '2026-12-31' }

beforeEach(async () => {
  await i18n.changeLanguage('de')
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

it('shows an empty state without an export when nothing has been announced', () => {
  render(<InspectionAppointment appointment={null} />)

  expect(screen.getByText('Keine Kontrolle angekündigt.')).toBeTruthy()
  expect(screen.queryByRole('button')).toBeNull()
})

it('displays the period and only shows a day and change notice when supplied', () => {
  const { rerender } = render(<InspectionAppointment appointment={appointment} />)

  expect(screen.getByText('Zeitraum')).toBeTruthy()
  expect(screen.getByText('Mo 28.12. bis Do 31.12.2026').textContent).toBe('Mo 28.12. bis Do 31.12.2026')
  expect(screen.queryByText(/Genauer Kontrolltag/)).toBeNull()
  expect(screen.queryByText('Termin geändert')).toBeNull()

  rerender(<InspectionAppointment appointment={{ ...appointment, exactDay: '2026-12-29', changed: true }} />)

  expect(screen.getByText('Genauer Kontrolltag: 29.12.2026')).toBeTruthy()
  expect(screen.getByText('Termin geändert')).toBeTruthy()
})

it('updates the displayed period when switching to English', async () => {
  render(<InspectionAppointment appointment={appointment} />)

  await act(async () => { await i18n.changeLanguage('en') })

  const period = 'Mon 28 Dec to Thu 31 Dec 2026'
  expect(screen.getByText(period).textContent).toBe(period)
})

it('downloads the displayed period as a calendar file', () => {
  const download = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
    expect(this.download).toBe('seezeit-kontrolltermin.ics')
    const payload = this.href.slice(this.href.indexOf(',') + 1)
    expect(decodeURIComponent(payload)).toContain('DTSTART;VALUE=DATE:20261228\r\nDTEND;VALUE=DATE:20270101')
  })
  render(<InspectionAppointment appointment={appointment} />)

  fireEvent.click(screen.getByRole('button', { name: 'In Kalender übernehmen' }))

  expect(download).toHaveBeenCalledOnce()
})
