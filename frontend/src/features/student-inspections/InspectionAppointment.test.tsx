// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import i18n from '@/lib/i18n'
import InspectionAppointment from './InspectionAppointment'

const appointment = { id: 42, startsOn: '2026-12-28', endsOn: '2026-12-31', appointmentChanged: false }
const rescheduledAppointment = {
  ...appointment,
  startsOn: '2027-01-04',
  endsOn: '2027-01-08',
  appointmentChanged: true,
}

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

it('displays the period and follows the backend change notice flag', () => {
  const { rerender } = render(<InspectionAppointment appointment={appointment} />)

  expect(screen.getByText('Zeitraum')).toBeTruthy()
  expect(screen.getByText('Mo 28.12. bis Do 31.12.2026').textContent).toBe('Mo 28.12. bis Do 31.12.2026')
  expect(screen.queryByText(/Genauer Kontrolltag/)).toBeNull()
  expect(screen.queryByText('Termin geändert')).toBeNull()

  rerender(<InspectionAppointment appointment={rescheduledAppointment} />)

  expect(screen.getByText('Mo 04.01. bis Fr 08.01.2027')).toBeTruthy()
  expect(screen.queryByText(/Genauer Kontrolltag/)).toBeNull()
  expect(screen.getByText('Termin geändert')).toBeTruthy()

  rerender(<InspectionAppointment appointment={{ ...rescheduledAppointment, appointmentChanged: false }} />)

  expect(screen.getByText('Mo 04.01. bis Fr 08.01.2027')).toBeTruthy()
  expect(screen.queryByText('Termin geändert')).toBeNull()
})

it('updates the displayed period and change notice when switching to English', async () => {
  render(<InspectionAppointment appointment={{ ...appointment, appointmentChanged: true }} />)

  await act(async () => { await i18n.changeLanguage('en') })

  const period = 'Mon 28 Dec to Thu 31 Dec 2026'
  expect(screen.getByText(period).textContent).toBe(period)
  expect(screen.getByText('Inspection date changed')).toBeTruthy()
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

it('exports the updated calendar boundaries after a reschedule', () => {
  const download = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
    expect(this.download).toBe('seezeit-kontrolltermin.ics')
    const payload = decodeURIComponent(this.href.slice(this.href.indexOf(',') + 1))
    expect(payload).toContain('DTSTART;VALUE=DATE:20270104\r\nDTEND;VALUE=DATE:20270109')
    expect(payload).not.toContain('DTSTART;VALUE=DATE:20261228')
  })
  const { rerender } = render(<InspectionAppointment appointment={appointment} />)
  rerender(<InspectionAppointment appointment={rescheduledAppointment} />)

  fireEvent.click(screen.getByRole('button', { name: 'In Kalender übernehmen' }))

  expect(download).toHaveBeenCalledOnce()
})
