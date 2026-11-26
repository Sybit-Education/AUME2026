// @vitest-environment jsdom
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { createMemoryRouter } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import AuthProvider from '@/lib/auth/AuthProvider'
import { api, type LoggedInUser } from '@/lib/auth/client'
import { ensureSession, invalidateSession } from '@/lib/auth/session'
import i18n from '@/lib/i18n'
import type { ResidenceDetail, ResidenceRow } from '@/features/inspection-overview/api'
import { inspectionOverviewRoutes } from '@/features/inspection-overview/routes'
import { homeRoutes } from './homeRoute'

vi.mock('@/lib/auth/client', () => ({
  initializeAuth: vi.fn(async () => ({ mode: 'dev' })),
  api: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}))

const clerk: LoggedInUser = { userKey: 'clerk', name: 'Sam', roles: ['CLERK'], privileges: ['CLERK_INSPECTION_OVERVIEW_READ'] }
const student: LoggedInUser = { userKey: 'student', name: 'Lea', roles: ['STUDENT'], privileges: ['GREETING_READ'] }
const appointmentPath = '/api/student/inspection-appointment'
const page = (content: unknown[]) => ({ content, page: { number: 0, size: 20, totalElements: content.length, totalPages: 1 } })
let signedIn: LoggedInUser
let rows: ResidenceRow[]
let detail: ResidenceDetail
let routers: ReturnType<typeof createMemoryRouter>[] = []

function mount(path = '/') {
  const router = createMemoryRouter([{
    path: '/',
    loader: ensureSession,
    HydrateFallback: () => null,
    children: [...homeRoutes, ...inspectionOverviewRoutes, { path: 'inspection-planning', element: <h1>planning</h1> }],
  }], { initialEntries: [path] })
  routers.push(router)
  render(<AuthProvider getRequestedPath={() => path}><RouterProvider router={router} /></AuthProvider>)
  return router
}

const overviewCalls = () => vi.mocked(api).mock.calls.map(([path]) => String(path))
  .filter((path) => path.startsWith('/api/inspection-overview?'))

beforeEach(async () => {
  vi.resetAllMocks()
  invalidateSession()
  signedIn = clerk
  detail = { id: 3, name: 'Sonne', rounds: [], openInspections: [] }
  rows = [
    {
      id: 1, name: 'Egg', lastCompleted: null, state: 'COUNTER_PROPOSED',
      current: { id: 7, startsOn: '2026-12-01', endsOn: '2026-12-03', kind: 'REGULAR', state: 'COUNTER_PROPOSED', controlled: 0, total: 2, passed: 0, failed: 0 },
    },
    { id: 2, name: 'Mond', lastCompleted: null, current: null, state: 'NONE' },
    {
      id: 3, name: 'Sonne', state: 'AGREED',
      lastCompleted: { id: 5, startsOn: '2026-05-10', endsOn: '2026-05-12', kind: 'REGULAR', state: 'COMPLETED', controlled: 100, total: 100, passed: 88, failed: 12 },
      current: { id: 8, startsOn: '2026-12-10', endsOn: '2026-12-12', kind: 'REGULAR', state: 'AGREED', controlled: 0, total: 100, passed: 0, failed: 0 },
    },
  ]
  await i18n.changeLanguage('de')
  vi.mocked(api).mockImplementation(async (path) => {
    if (path === '/api/me') return Response.json(signedIn)
    if (path === '/api/greeting') return new Response('Hallo Lea, willkommen in Sonne.')
    if (path === appointmentPath) {
      return Response.json({
        id: 42, startsOn: '2026-12-07', endsOn: '2026-12-11', appointmentChanged: false, showInitialInspection: true,
      })
    }
    if (path === '/api/inspection-overview/counts') return Response.json({ counterProposed: 1, agreedNotAnnounced: 1 })
    if (String(path).startsWith('/api/inspection-overview?')) return Response.json(page(rows))
    if (String(path) === '/api/inspection-overview/3') return Response.json(detail)
    throw new Error(`Unexpected API call: ${path}`)
  })
})

afterEach(() => {
  cleanup()
  routers.forEach((router) => router.dispose())
  routers = []
})

it('shows one row per residence with last completed round, current round and next step', async () => {
  mount()
  const table = within(await screen.findByRole('table'))
  const [, egg, mond, sonne] = table.getAllByRole('row')
  expect(within(egg).getByText('Gegenvorschlag der Objektbetreuung')).toBeDefined()
  expect(within(egg).getByRole('link', { name: 'Gegenvorschlag prüfen' }).getAttribute('href')).toBe('/inspection-planning?review=7')
  expect(within(mond).getByText('Noch keine Kontrolle erfasst')).toBeDefined()
  expect(within(mond).getByRole('link', { name: 'Kontrolle planen' }).getAttribute('href')).toBe('/inspection-planning?residence=2')
  expect(within(sonne).getByText('88 bestanden · 12 Nachkontrolle')).toBeDefined()
  expect(within(sonne).getByText('Abgestimmt, nicht angekündigt')).toBeDefined()
  expect(within(sonne).getByRole('button', { name: 'Ankündigen' })).toBeDefined()
  expect(screen.queryByText(/Sam/)).toBeNull()
  expect(vi.mocked(api).mock.calls.some(([path]) => path === appointmentPath)).toBe(false)
})

it('filters by counter tiles, status and inspection type', async () => {
  const router = mount()
  fireEvent.click(await screen.findByRole('button', { name: /Gegenvorschlag offen/ }))
  await vi.waitFor(() => expect(router.state.location.search).toBe('?state=COUNTER_PROPOSED'))
  expect(screen.getByRole('button', { name: /Gegenvorschlag offen/ }).getAttribute('aria-pressed')).toBe('true')
  await vi.waitFor(() => expect(overviewCalls()).toContain('/api/inspection-overview?page=0&state=COUNTER_PROPOSED'))

  fireEvent.change(screen.getByLabelText('Kontrollart'), { target: { value: 'REGULAR' } })
  await vi.waitFor(() => expect(overviewCalls()).toContain('/api/inspection-overview?page=0&state=COUNTER_PROPOSED&kind=REGULAR'))

  fireEvent.click(screen.getByRole('button', { name: 'Filter zurücksetzen' }))
  await vi.waitFor(() => expect(router.state.location.search).toBe(''))
  expect((screen.getByLabelText('Status') as HTMLSelectElement).value).toBe('')
})

it('ignores unknown filter values from the address bar', async () => {
  mount('/?state=DONE&kind=SPECIAL')
  await screen.findByRole('table')
  expect(overviewCalls()).toEqual(['/api/inspection-overview?page=0'])
})

it('reloads the overview when the tab becomes visible again', async () => {
  mount()
  await screen.findByRole('table')
  document.dispatchEvent(new Event('visibilitychange'))
  await vi.waitFor(() => expect(overviewCalls().length).toBe(2))
})

it('opens rounds and only open single-flat inspections of a residence', async () => {
  detail = {
    id: 3,
    name: 'Sonne',
    rounds: [
      { id: 8, startsOn: '2026-11-18', endsOn: '2026-11-20', kind: 'REGULAR', state: 'RUNNING', controlled: 37, total: 100, passed: 33, failed: 4 },
      { id: 5, startsOn: '2026-05-10', endsOn: '2026-05-12', kind: 'REGULAR', state: 'COMPLETED', controlled: 100, total: 100, passed: 88, failed: 12 },
    ],
    openInspections: [
      { flat: '3.08', kind: 'FOLLOW_UP', startsOn: '2026-11-23', endsOn: '2026-11-24', outcome: 'OPEN' },
    ],
  }
  mount()
  fireEvent.click(await screen.findByRole('link', { name: 'Sonne' }))
  expect(await screen.findByRole('heading', { name: 'Kontrollrunden · Sonne' })).toBeDefined()
  expect(screen.getByText('Läuft · 37 von 100')).toBeDefined()
  expect(screen.getByText('88 bestanden · 12 Nachkontrolle')).toBeDefined()
  expect(screen.getByText('WG 3.08')).toBeDefined()
  expect(screen.getByText('Noch offen')).toBeDefined()
  expect(screen.queryByText('Protokoll liegt vor')).toBeNull()
})

it('keeps the dashboard for users without the overview privilege', async () => {
  signedIn = student
  mount()
  expect(await screen.findByText('Willkommen, Lea.')).toBeDefined()
  expect(screen.getByText('Hallo Lea, willkommen in Sonne.')).toBeDefined()
  const appointment = within(await screen.findByRole('region', { name: 'Nächste Kontrolle' }))
  expect(await appointment.findByText('Erstkontrolle')).toBeDefined()
  expect(appointment.getByRole('link', { name: 'Details' }).getAttribute('href')).toBe('/student/inspection/appointment')
  expect(overviewCalls()).toEqual([])
})
