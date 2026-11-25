// @vitest-environment jsdom
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { createMemoryRouter } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import AuthProvider from '@/lib/auth/AuthProvider'
import { api, type LoggedInUser } from '@/lib/auth/client'
import { ensureSession, invalidateSession } from '@/lib/auth/session'
import i18n from '@/lib/i18n'
import type { InspectionRow, ResidenceRow } from '@/features/inspection-overview/api'
import { inspectionOverviewRoutes } from '@/features/inspection-overview/routes'
import { homeRoutes } from './homeRoute'

vi.mock('@/lib/auth/client', () => ({
  initializeAuth: vi.fn(async () => ({ mode: 'dev' })),
  api: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}))

const clerk: LoggedInUser = { userKey: 'clerk', name: 'Sam', roles: ['CLERK'], privileges: ['CLERK_INSPECTION_OVERVIEW_READ'] }
const student: LoggedInUser = { userKey: 'student', name: 'Lea', roles: ['STUDENT'], privileges: [] }
const page = (content: unknown[]) => ({ content, page: { number: 0, size: 20, totalElements: content.length, totalPages: 1 } })
let signedIn: LoggedInUser
let rows: ResidenceRow[]
let inspections: InspectionRow[]
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
  inspections = []
  rows = [
    { id: 1, name: 'Egg', lastInspection: null, upcoming: { id: 7, startsOn: '2026-12-01', endsOn: '2026-12-03', kind: 'REGULAR' }, state: 'COUNTER_PROPOSED' },
    { id: 2, name: 'Mond', lastInspection: null, upcoming: null, state: 'NONE' },
    { id: 3, name: 'Sonne', lastInspection: { on: '2026-10-30', kind: 'FOLLOW_UP' }, upcoming: { id: 8, startsOn: '2026-12-10', endsOn: '2026-12-10', kind: 'REGULAR' }, state: 'AGREED' },
  ]
  await i18n.changeLanguage('de')
  vi.mocked(api).mockImplementation(async (path) => {
    if (path === '/api/me') return Response.json(signedIn)
    if (path === '/api/inspection-overview/counts') return Response.json({ counterProposed: 1, agreedNotAnnounced: 1 })
    if (String(path).startsWith('/api/inspection-overview?')) return Response.json(page(rows))
    if (String(path).startsWith('/api/inspection-overview/3?')) return Response.json(page(inspections))
    throw new Error(`Unexpected API call: ${path}`)
  })
})

afterEach(() => {
  cleanup()
  routers.forEach((router) => router.dispose())
  routers = []
})

it('shows one row per residence with last and upcoming inspection, status and next step', async () => {
  mount()
  const table = within(await screen.findByRole('table'))
  const [, egg, mond, sonne] = table.getAllByRole('row')
  expect(within(egg).getByText('Gegenvorschlag der Objektbetreuung')).toBeDefined()
  expect(within(egg).getByRole('link', { name: 'Gegenvorschlag prüfen' }).getAttribute('href')).toBe('/inspection-planning?review=7')
  expect(within(mond).getByText('Noch keine Kontrolle erfasst')).toBeDefined()
  expect(within(mond).getByRole('link', { name: 'Kontrolle planen' }).getAttribute('href')).toBe('/inspection-planning?residence=2')
  expect(within(sonne).getByText('30.10.2026 · Nachkontrolle')).toBeDefined()
  expect(within(sonne).getByText('10.12.2026 · Reguläre Kontrolle')).toBeDefined()
  expect(within(sonne).getByText('Abgestimmt, nicht angekündigt')).toBeDefined()
  expect(within(sonne).getByRole('button', { name: 'Ankündigen' })).toBeDefined()
  expect(screen.queryByText(/Sam/)).toBeNull()
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

it('opens the inspections of a residence including single-flat inspections', async () => {
  inspections = [
    { flat: null, kind: 'REGULAR', startsOn: '2026-12-10', endsOn: '2026-12-12', status: 'AGREED' },
    { flat: 'S-01', kind: 'FOLLOW_UP', startsOn: '2026-10-30', endsOn: '2026-10-30', status: 'PUBLISHED' },
  ]
  mount()
  fireEvent.click(await screen.findByRole('link', { name: 'Sonne' }))
  expect(await screen.findByRole('heading', { name: 'Kontrollen · Sonne' })).toBeDefined()
  const [, whole, single] = screen.getAllByRole('row')
  expect(within(whole).getByText('Ganze Anlage')).toBeDefined()
  expect(within(whole).getByText('10.–12.12.2026')).toBeDefined()
  expect(within(single).getByText('WG S-01')).toBeDefined()
  expect(within(single).getByText('Nachkontrolle')).toBeDefined()
  expect(within(single).getByText('Ergebnis zugestellt')).toBeDefined()
})

it('keeps the dashboard for users without the overview privilege', async () => {
  signedIn = student
  mount()
  expect(await screen.findByText('Willkommen, Lea.')).toBeDefined()
  expect(overviewCalls()).toEqual([])
})
