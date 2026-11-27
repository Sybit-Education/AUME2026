// @vitest-environment jsdom
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { cleanup, render, screen, within } from '@testing-library/react'
import { createMemoryRouter } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import AuthProvider from '@/lib/auth/AuthProvider'
import { protectedLoader } from '@/lib/auth/loaders'
import OverviewPage from './OverviewPage'
import { CARETAKER_OVERVIEW_PRIVILEGE, loadCurrentInspections } from './currentInspections'
import { invalidateSession } from '@/lib/auth/session'
import i18n from '@/lib/i18n'
import { api, type LoggedInUser } from '@/lib/auth/client'
import type { InspectionSummary } from './api'
import type { InspectionRequest } from './requestsApi'

vi.mock('@/lib/auth/client', () => ({
  initializeAuth: vi.fn(async () => ({ mode: 'dev' })),
  api: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}))

const caretaker: LoggedInUser = {
  userKey: 'caretaker', name: 'Fischer', roles: ['CARETAKER'],
  privileges: ['INSPECTION_OVERVIEW_READ', 'INSPECTION_PERFORM', 'INSPECTION_PERIOD_RESPOND', 'RESIDENCE_READ'],
}
const inspection = (id: number, flatLabel: string, round: number): InspectionSummary => ({
  id, flatLabel, residenceName: 'Sonne', status: 'PLANNED', round, startsOn: round > 0 ? null : '2026-11-18',
  endsOn: round > 0 ? null : '2026-11-20', windowStart: null, windowEnd: null, periodKind: round > 0 ? null : 'REGULAR',
})
const request = (id: number, status: InspectionRequest['status']): InspectionRequest => ({
  id, residenceName: 'Mond', kind: 'REGULAR', status, startsOn: '2026-12-01', endsOn: '2026-12-03',
  windowStart: null, windowEnd: null, flatCount: 2, flatLabels: null, counter: null, version: 0,
})
const page = (content: unknown[]) => ({ content, page: { number: 0, size: 100, totalElements: content.length, totalPages: 1 } })
let signedIn: LoggedInUser

beforeEach(async () => {
  vi.resetAllMocks()
  invalidateSession()
  signedIn = caretaker
  await i18n.changeLanguage('de')
  vi.mocked(api).mockImplementation(async (path) => {
    if (path === '/api/me') return Response.json(signedIn)
    if (String(path).startsWith('/api/inspections?')) {
      return Response.json(page([inspection(7, '4.12', 0), inspection(8, '4.14', 0), inspection(9, '3.08', 1)]))
    }
    if (String(path).startsWith('/api/inspection-periods/assigned')) {
      return Response.json(page([request(3, 'WAITING_FOR_CARETAKER'), request(4, 'AGREED')]))
    }
    throw new Error(`Unexpected API call: ${path}`)
  })
})
afterEach(cleanup)

function mount() {
  const router = createMemoryRouter([{ path: '/', Component: OverviewPage, loader: protectedLoader(CARETAKER_OVERVIEW_PRIVILEGE, ({ request }, user) => loadCurrentInspections(user, request.signal)) }], { initialEntries: ['/'] })
  render(<AuthProvider getRequestedPath={() => '/'}><RouterProvider router={router} /></AuthProvider>)
}

it('shows counts linking to the inspections page', async () => {
  mount()
  expect(await screen.findByRole('heading', { name: 'Hallo Fischer' })).toBeTruthy()
  expect(screen.getByText('Vorschläge zu beantworten').previousElementSibling?.textContent).toBe('1')
  expect(screen.getByText('Angekündigte Kontrollen').previousElementSibling?.textContent).toBe('2')
  expect(screen.getByText('Nachkontrollen').closest('a')?.getAttribute('href')).toBe('/inspections')
  expect(screen.queryByText('Offene Schäden')).toBeNull()
})

it('shows the requests and upcoming tables of the inspections page', async () => {
  mount()
  const requests = within(await screen.findByRole('table', { name: 'Anfragen' }))
  expect(requests.getByRole('link', { name: 'Vorschlag für Mond beantworten' }).getAttribute('href')).toBe('/inspections?answer=3')
  const upcoming = within(screen.getByRole('table', { name: 'Anstehende Kontrollen' }))
  expect(upcoming.getByText('4.12')).toBeTruthy()
  expect(upcoming.getByText('3.08')).toBeTruthy()
})

it('leaves out requests without the privilege to answer them', async () => {
  signedIn = { ...caretaker, privileges: ['INSPECTION_OVERVIEW_READ', 'INSPECTION_PERFORM'] }
  mount()
  expect(await screen.findByRole('table', { name: 'Anstehende Kontrollen' })).toBeTruthy()
  expect(screen.queryByRole('table', { name: 'Anfragen' })).toBeNull()
  expect(screen.queryByText('Vorschläge zu beantworten')).toBeNull()
})
