// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import i18n from '@/lib/i18n'
import InspectionAppointment from './InspectionAppointment'
import type { AppointmentDetails } from './api'

const appointment = {
  id: 42, startsOn: '2026-12-28', endsOn: '2026-12-31', appointmentChanged: false, showInitialInspection: true,
}
const rescheduledAppointment = {
  ...appointment,
  startsOn: '2027-01-04',
  endsOn: '2027-01-08',
  appointmentChanged: true,
  showInitialInspection: false,
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
  expect(screen.queryByText('Erstkontrolle')).toBeNull()
  expect(screen.queryByText('Termin geändert')).toBeNull()
})

it('displays the period and follows the backend change notice flag', () => {
  const { rerender } = render(<InspectionAppointment appointment={appointment} />)

  expect(screen.getByText('Zeitraum')).toBeTruthy()
  expect(screen.getByText('Mo 28.12. bis Do 31.12.2026').textContent).toBe('Mo 28.12. bis Do 31.12.2026')
  expect(screen.queryByText(/Genauer Kontrolltag/)).toBeNull()
  expect(screen.queryByText('Termin geändert')).toBeNull()
  const initialLabel = screen.getByText('Erstkontrolle')
  expect(initialLabel.className).toBe('chip inf')
  expect(initialLabel.parentElement).toBe(screen.getByRole('heading', { name: 'Kontrolltermin' }).parentElement)

  rerender(<InspectionAppointment appointment={rescheduledAppointment} />)

  expect(screen.getByText('Mo 04.01. bis Fr 08.01.2027')).toBeTruthy()
  expect(screen.queryByText(/Genauer Kontrolltag/)).toBeNull()
  expect(screen.getByText('Termin geändert')).toBeTruthy()
  const changedLabel = screen.getByText('Termin geändert')
  expect(changedLabel.className).toBe('chip wr')
  expect(changedLabel.parentElement).toBe(screen.getByRole('heading', { name: 'Kontrolltermin' }).parentElement)
  expect(screen.queryByText('Erstkontrolle')).toBeNull()

  rerender(<InspectionAppointment appointment={{ ...rescheduledAppointment, appointmentChanged: false }} />)

  expect(screen.getByText('Mo 04.01. bis Fr 08.01.2027')).toBeTruthy()
  expect(screen.queryByText('Termin geändert')).toBeNull()
  expect(screen.queryByText('Erstkontrolle')).toBeNull()
})

it('updates the displayed period and change notice when switching to English', async () => {
  render(<InspectionAppointment appointment={{ ...appointment, appointmentChanged: true, showInitialInspection: false }} />)

  await act(async () => { await i18n.changeLanguage('en') })

  const period = 'Mon 28 Dec to Thu 31 Dec 2026'
  expect(screen.getByText(period).textContent).toBe(period)
  expect(screen.getByText('Inspection date changed')).toBeTruthy()
})

it('translates the initial inspection label on the summary card', async () => {
  render(
    <MemoryRouter>
      <InspectionAppointment appointment={appointment} variant="summary" />
    </MemoryRouter>,
  )

  await act(async () => { await i18n.changeLanguage('en') })

  expect(screen.getByText('Initial inspection').className).toBe('chip inf')
})

it.each([{ loading: true }, { loadFailed: true }])('omits both labels for %j', (state) => {
  render(<InspectionAppointment appointment={null} {...state} />)

  expect(screen.queryByText('Erstkontrolle')).toBeNull()
  expect(screen.queryByText('Termin geändert')).toBeNull()
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

it.each([
  { status: 'ANNOUNCED', display: 'CURRENT', heading: 'Kontrollzeitraum', label: 'Angekündigt' },
  { status: null, display: 'PAST', heading: 'Kontrollzeitraum', label: null },
  { status: 'RESULT_AVAILABLE', display: 'PAST', heading: 'Letzte Kontrolle', label: 'Ergebnis verfügbar' },
] as const)('shows the details for $status / $display', ({ status, display, heading, label }) => {
  const details: AppointmentDetails = { ...appointment, status, display, windowStart: '09:00', windowEnd: '16:00' }
  render(<MemoryRouter><InspectionAppointment appointment={details} canReadChecklist /></MemoryRouter>)

  expect(screen.getByRole('heading', { name: heading })).toBeTruthy()
  expect(screen.getByText('Erstkontrolle').className).toBe('chip inf')
  if (label) {
    const timeline = screen.getByRole('list')
    const steps = within(timeline).getAllByRole('listitem')
    expect(steps.map((step) => step.textContent)).toEqual(['Angekündigt', 'Ergebnis verfügbar'])
    expect(within(timeline).getByRole('listitem', { current: 'step' }).textContent).toBe(label)
    const complete = status === 'RESULT_AVAILABLE'
    expect(timeline.getAttribute('data-complete')).toBe(String(complete))
    expect(steps.map((step) => step.getAttribute('data-reached'))).toEqual(['true', String(complete)])
    expect(screen.getByText(label).closest('.chip')).toBeNull()
  } else {
    expect(screen.queryByRole('list')).toBeNull()
  }
  expect(screen.queryByText('Durchgeführt')).toBeNull()
  expect(screen.getByRole('link', { name: 'Checkliste' }).getAttribute('href')).toBe('/student/inspection/checklist')
  expect(Boolean(screen.queryByRole('button', { name: 'In Kalender übernehmen' }))).toBe(display === 'CURRENT')
})

it.each([
  { windowStart: null, windowEnd: null },
  { windowStart: '09:00', windowEnd: null },
  { windowStart: '25:00', windowEnd: '16:00' },
  { windowStart: '16:00', windowEnd: '09:00' },
])('omits the arrival notice for invalid or incomplete windows (%j)', (window) => {
  const details: AppointmentDetails = { ...appointment, ...window, display: 'UPCOMING', status: 'ANNOUNCED' }
  render(<InspectionAppointment appointment={details} />)
  expect(screen.queryByText(/Die Objektbetreuung kommt zwischen/)).toBeNull()
})

it('translates the announcement and its time window in English', async () => {
  const details: AppointmentDetails = {
    ...appointment, display: 'UPCOMING', status: 'ANNOUNCED', windowStart: '09:00', windowEnd: '16:00',
  }
  render(<InspectionAppointment appointment={details} />)

  await act(async () => { await i18n.changeLanguage('en') })
  expect(screen.getByText('Announced')).toBeTruthy()
  expect(screen.getByText('Result available')).toBeTruthy()
  expect(screen.getByText('The caretaker will come between 09:00 and 16:00.')).toBeTruthy()
})

it('keeps the change label on the summary card without showing the timeline', () => {
  const details: AppointmentDetails = {
    ...rescheduledAppointment, display: 'UPCOMING', status: 'ANNOUNCED', windowStart: '09:00', windowEnd: '16:00',
  }
  const { rerender } = render(
    <MemoryRouter><InspectionAppointment appointment={details} variant="summary" /></MemoryRouter>,
  )
  expect(screen.getByText('Termin geändert').className).toBe('chip wr')
  expect(screen.queryByRole('list')).toBeNull()

  rerender(<MemoryRouter><InspectionAppointment appointment={details} /></MemoryRouter>)
  expect(screen.getByText('Termin geändert').className).toBe('chip wr')
  expect(screen.getByRole('list')).toBeTruthy()
})
