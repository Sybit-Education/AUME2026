// @vitest-environment jsdom
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { createMemoryRouter } from 'react-router'
// eslint-disable-next-line no-restricted-imports -- Exercise the real app in routing integration tests.
import App from '@/app/App'
// eslint-disable-next-line no-restricted-imports -- Use the production route tree in this test suite.
import { createAppRoutes } from '@/app/router'
import { invalidateSession } from '@/lib/auth/session'
import { UiError } from '@/lib/errors'
import i18n from '@/lib/i18n'
import { api, login, type LoggedInUser } from '@/lib/auth/client'
import type { UpcomingAppointment } from './api'

vi.mock('@/lib/auth/client', () => ({
  initializeAuth: vi.fn(async () => ({ mode: 'dev' })),
  api: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}))

const student: LoggedInUser = { userKey: 'student', name: 'Alex', roles: ['STUDENT'], privileges: ['GREETING_READ'] }
const clerk: LoggedInUser = { userKey: 'clerk', name: 'Sam', roles: ['CLERK'], privileges: ['RESIDENCE_READ'] }
const unchangedAppointment = {
  id: 42, startsOn: '2026-12-07', endsOn: '2026-12-11', appointmentChanged: false, showInitialInspection: true,
} satisfies UpcomingAppointment

let signedIn: LoggedInUser | null
let studentInspectionAppointment: UpcomingAppointment | null
let studentInspectionFailure: number | Error | null
let studentInspectionResponse: Promise<Response> | undefined
let routers: ReturnType<typeof createMemoryRouter>[] = []

function mount(path = '/') {
  const router = createMemoryRouter(createAppRoutes(), { initialEntries: [path] })
  routers.push(router)
  render(<App router={router} />)
  return router
}

function deferStudentInspectionResponse() {
  let resolve!: (response: Response) => void
  studentInspectionResponse = new Promise<Response>((complete) => { resolve = complete })
  return resolve
}

beforeEach(async () => {
  vi.resetAllMocks()
  invalidateSession()
  signedIn = null
  studentInspectionAppointment = null
  studentInspectionFailure = null
  studentInspectionResponse = undefined
  await i18n.changeLanguage('de')
  vi.mocked(login).mockImplementation(async (key) => { signedIn = key === 'student' ? student : clerk })
  vi.mocked(api).mockImplementation(async (path) => {
    if (path === '/api/me') return signedIn ? Response.json(signedIn) : new Response(null, { status: 401 })
    if (path === '/api/greeting') {
      const name = signedIn?.name
      return new Response(i18n.resolvedLanguage === 'en'
        ? `Hello ${name}, welcome to Sonne.`
        : `Hallo ${name}, willkommen in Sonne.`)
    }
    if (path === '/api/student/inspection-appointment') {
      if (studentInspectionResponse) return studentInspectionResponse
      if (studentInspectionFailure instanceof Error) throw studentInspectionFailure
      if (studentInspectionFailure !== null) return new Response(null, { status: studentInspectionFailure })
      return studentInspectionAppointment ? Response.json(studentInspectionAppointment) : new Response(null, { status: 204 })
    }
    throw new Error(`Unexpected API call: ${path}`)
  })
})
afterEach(() => {
  cleanup()
  routers.forEach((router) => router.dispose())
  routers = []
})

it('shows the backend appointment without adding an exact date or change notice', async () => {
  signedIn = student
  studentInspectionAppointment = unchangedAppointment
  mount()

  await screen.findByRole('heading', { name: 'Nächste Kontrolle' })
  const period = 'Mo 07.12. bis Fr 11.12.2026'
  await screen.findByText(period)
  expect(screen.getByText(period).textContent).toBe(period)
  expect(screen.queryByText(/Genauer Kontrolltag/)).toBeNull()
  expect(screen.queryByText('Termin geändert')).toBeNull()
  expect(screen.getByText('Erstkontrolle').parentElement)
    .toBe(screen.getByRole('heading', { name: 'Nächste Kontrolle' }).parentElement)
  expect(screen.queryByText('Beispieltermin für die lokale Entwicklung')).toBeNull()
  expect(screen.getByRole('link', { name: 'Details' }).getAttribute('href')).toBe('/student/inspection/appointment')
  fireEvent.click(screen.getByRole('link', { name: 'Details' }))
  await screen.findByRole('heading', { name: 'Kontrolle' })
  expect(screen.getByText('Erstkontrolle').parentElement)
    .toBe(screen.getByRole('heading', { name: 'Kontrolle' }).parentElement)
  expect(screen.getByText(period).textContent).toBe(period)
  expect(screen.getByRole('button', { name: 'In Kalender übernehmen' })).toBeTruthy()
})

it('shows the rescheduled period and notice on the dashboard and appointment route', async () => {
  signedIn = student
  studentInspectionAppointment = {
    ...unchangedAppointment,
    startsOn: '2026-12-14',
    endsOn: '2026-12-18',
    appointmentChanged: true,
    showInitialInspection: false,
  }
  const router = mount()

  const dashboardCard = within(await screen.findByRole('region', { name: 'Nächste Kontrolle' }))
  await dashboardCard.findByText('Termin geändert')
  expect(dashboardCard.getByText('Termin geändert').parentElement)
    .toBe(dashboardCard.getByRole('heading', { name: 'Nächste Kontrolle' }).parentElement)
  expect(dashboardCard.getByText('Mo 14.12. bis Fr 18.12.2026')).toBeTruthy()
  fireEvent.click(dashboardCard.getByRole('link', { name: 'Details' }))

  const detailsCard = within(await screen.findByRole('region', { name: 'Kontrolle' }))
  expect(router.state.location.pathname).toBe('/student/inspection/appointment')
  expect(detailsCard.getByText('Termin geändert')).toBeTruthy()
  expect(detailsCard.getByText('Termin geändert').parentElement)
    .toBe(detailsCard.getByRole('heading', { name: 'Kontrolle' }).parentElement)
  expect(detailsCard.getByText('Mo 14.12. bis Fr 18.12.2026')).toBeTruthy()
})

it('shows the empty appointment card when the backend has no appointment', async () => {
  signedIn = student
  mount()

  await screen.findByText('Keine Kontrolle angekündigt.')
  expect(screen.queryByText('Beispieltermin für die lokale Entwicklung')).toBeNull()
  expect(screen.queryByRole('button', { name: 'In Kalender übernehmen' })).toBeNull()
})

it.each(['HTTP 500', 'a network error'])('keeps the personalized dashboard usable after %s from the student inspection API', async (failure) => {
  signedIn = { ...student, name: 'Mia' }
  studentInspectionFailure = failure === 'HTTP 500' ? 500 : new TypeError('Failed to fetch')
  mount()

  const card = within(await screen.findByRole('region', { name: 'Nächste Kontrolle' }))
  await card.findByRole('alert')
  expect(card.getByRole('alert').textContent).toBe('Der Kontrolltermin konnte nicht geladen werden.')
  expect(card.queryByText('Keine Kontrolle angekündigt.')).toBeNull()
  expect(screen.getByText('Willkommen, Mia.')).toBeTruthy()
  expect(screen.getByText('Hallo Mia, willkommen in Sonne.')).toBeTruthy()

  await act(async () => { await i18n.changeLanguage('en') })
  await screen.findByText('Hello Mia, welcome to Sonne.')
  expect(screen.getByText('Welcome, Mia.')).toBeTruthy()
  expect(card.getByRole('alert').textContent).toBe('The inspection appointment could not be loaded.')
})

it('shows the dashboard greeting while the student inspection request is pending', async () => {
  signedIn = student
  const resolve = deferStudentInspectionResponse()
  mount()

  const card = within(await screen.findByRole('region', { name: 'Nächste Kontrolle' }))
  expect(screen.getByText('Willkommen, Alex.')).toBeTruthy()
  expect(screen.getByText('Hallo Alex, willkommen in Sonne.')).toBeTruthy()
  expect(card.getByRole('status').textContent).toContain('Wird geladen')
  expect(card.queryByText('Keine Kontrolle angekündigt.')).toBeNull()

  await act(async () => { resolve(new Response(null, { status: 204 })) })
  await card.findByText('Keine Kontrolle angekündigt.')
})

it.each([200, 401, 403, 500])('ignores a late student inspection HTTP %s after leaving the dashboard', async (status) => {
  signedIn = student
  const resolve = deferStudentInspectionResponse()
  const router = mount()
  const request = () => vi.mocked(api).mock.calls.find(([path]) => path === '/api/student/inspection-appointment')
  await waitFor(() => { expect(request()).toBeDefined() })
  const signal = request()?.[1]?.signal
  expect(signal?.aborted).toBe(false)

  await act(async () => { await router.navigate('/does-not-exist') })
  await screen.findByRole('heading', { name: 'Seite nicht gefunden' })
  expect(signal?.aborted).toBe(true)
  await act(async () => {
    resolve(status === 200
      ? Response.json(unchangedAppointment)
      : new Response(null, { status }))
  })

  expect(router.state.location.pathname).toBe('/does-not-exist')
  expect(screen.getByRole('button', { name: 'Abmelden' })).toBeTruthy()
  expect(screen.queryByRole('alert')).toBeNull()
  expect(screen.queryByText('Mo 07.12. bis Fr 11.12.2026')).toBeNull()
})

it('reformats the loaded inspection when changing language without fetching it again', async () => {
  signedIn = student
  studentInspectionAppointment = unchangedAppointment
  mount()

  await screen.findByText('Mo 07.12. bis Fr 11.12.2026')
  await act(async () => { await i18n.changeLanguage('en') })
  await screen.findByText('Hello Alex, welcome to Sonne.')
  await screen.findByText('Mon 7 Dec to Fri 11 Dec 2026')
  expect(vi.mocked(api).mock.calls.filter(([path]) => path === '/api/student/inspection-appointment')).toHaveLength(1)
})

it('returns to login when the student inspection dashboard request reports an expired session', async () => {
  signedIn = student
  studentInspectionFailure = 401
  const router = mount()

  await screen.findByRole('button', { name: 'Studierende' })
  expect(router.state.location.pathname).toBe('/login')
  expect(new URLSearchParams(router.state.location.search).get('returnTo')).toBe('/')
  expect(screen.queryByRole('region', { name: 'Nächste Kontrolle' })).toBeNull()
})

it('returns to login when token renewal fails during the student inspection dashboard request', async () => {
  signedIn = student
  studentInspectionFailure = new UiError('errors.sessionExpired')
  const router = mount()

  await screen.findByRole('button', { name: 'Studierende' })
  expect(router.state.location.pathname).toBe('/login')
  expect(new URLSearchParams(router.state.location.search).get('returnTo')).toBe('/')
  expect(screen.queryByRole('alert')).toBeNull()
})

it('redirects to forbidden when the student inspection dashboard request is denied', async () => {
  signedIn = student
  studentInspectionFailure = 403
  const router = mount()

  await screen.findByRole('heading', { name: 'Zugriff nicht erlaubt' })
  expect(router.state.location.pathname).toBe('/forbidden')
  expect(screen.queryByRole('region', { name: 'Nächste Kontrolle' })).toBeNull()
})

it.each(['CLERK', 'CARETAKER'])('does not request or show an appointment on the %s dashboard', async (role) => {
  signedIn = { ...clerk, roles: [role] }
  mount()

  await screen.findByRole('link', { name: 'Zugewiesene Wohnanlagen öffnen' })
  expect(screen.queryByRole('heading', { name: 'Nächste Kontrolle' })).toBeNull()
  expect(vi.mocked(api).mock.calls.some(([path]) => path === '/api/student/inspection-appointment')).toBe(false)
})

it('shows the English inspection heading on direct access as a student', async () => {
  signedIn = student
  await i18n.changeLanguage('en')
  mount('/student/inspection/appointment')

  await screen.findByRole('heading', { name: /^Inspection$/ })
})

it('returns to the control details after signing in and shows the empty state', async () => {
  const router = mount('/student/inspection/appointment')
  const loginButton = await screen.findByRole('button', { name: 'Studierende' })
  expect(vi.mocked(api).mock.calls.every(([path]) => path === '/api/me')).toBe(true)
  fireEvent.click(loginButton)

  await screen.findByRole('heading', { name: 'Kontrolle' })
  expect(screen.getByText('Keine Kontrolle angekündigt.')).toBeTruthy()
  expect(login).toHaveBeenCalledWith('student', '/student/inspection/appointment')
  expect(router.state.location.pathname).toBe('/student/inspection/appointment')
})

it('redirects staff away from the control details without requesting an appointment', async () => {
  signedIn = clerk
  const router = mount('/student/inspection/appointment')

  await screen.findByRole('heading', { name: 'Zugriff nicht erlaubt' })
  expect(router.state.location.pathname).toBe('/forbidden')
  expect(vi.mocked(api).mock.calls.every(([path]) => path === '/api/me')).toBe(true)
})

it('shows a translated error when the control details cannot be loaded', async () => {
  signedIn = student
  studentInspectionFailure = 500
  mount('/student/inspection/appointment')

  await screen.findByRole('heading', { name: 'Die Seite konnte nicht geladen werden.' })
  expect(screen.getByRole('alert').textContent).toBe('Der Kontrolltermin konnte nicht geladen werden.')
  expect(screen.queryByText('Keine Kontrolle angekündigt.')).toBeNull()
})
