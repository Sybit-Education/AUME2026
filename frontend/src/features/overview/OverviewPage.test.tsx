// @vitest-environment jsdom
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { cleanup, render, screen, within } from '@testing-library/react'
import { createMemoryRouter } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import AuthProvider from '@/lib/auth/AuthProvider'
import { protectedLoader } from '@/lib/auth/loaders'
import OverviewPage from './OverviewPage'
import { CARETAKER_OVERVIEW_PRIVILEGE, loadOverview } from './routes'
import { invalidateSession } from '@/lib/auth/session'
import i18n from '@/lib/i18n'
import { api, type LoggedInUser } from '@/lib/auth/client'
import type { CaretakerOverview } from './api'

vi.mock('@/lib/auth/client', () => ({
  initializeAuth: vi.fn(async () => ({ mode: 'dev' })),
  api: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}))

const caretaker: LoggedInUser = {
  userKey: 'caretaker', name: 'Fischer', roles: ['CARETAKER'], privileges: ['INSPECTION_OVERVIEW_READ', 'INSPECTION_PERFORM', 'RESIDENCE_READ'],
}
const overview: CaretakerOverview = {
  proposals: 1, announced: 1, followUps: 1, openDamages: null,
  upcoming: [
    { id: 7, flatLabel: '4.12', residenceName: 'Sonne', kind: 'INITIAL', round: 0, state: 'ANNOUNCED', periodId: 2, canRespond: false, startsOn: '2026-11-18', endsOn: '2026-11-20', dueOn: null },
    { id: 9, flatLabel: '3.08', residenceName: 'Sonne', kind: 'FOLLOW_UP', round: 1, state: 'ANNOUNCED', periodId: null, canRespond: false, startsOn: null, endsOn: null, dueOn: '2026-11-24' },
    { id: 8, flatLabel: '3.10', residenceName: 'Sonne', kind: 'INITIAL', round: 0, state: 'PROPOSED', periodId: 3, canRespond: true, startsOn: '2026-11-25', endsOn: '2026-11-27', dueOn: null },
  ],
}
let signedIn: LoggedInUser

beforeEach(async () => {
  vi.resetAllMocks()
  invalidateSession()
  signedIn = caretaker
  await i18n.changeLanguage('de')
  vi.mocked(api).mockImplementation(async (path) => {
    if (path === '/api/me') return Response.json(signedIn)
    if (path === '/api/overview/caretaker') return Response.json(overview)
    throw new Error(`Unexpected API call: ${path}`)
  })
})
afterEach(cleanup)

function mount() {
  const router = createMemoryRouter([{ path: '/', Component: OverviewPage, loader: protectedLoader(CARETAKER_OVERVIEW_PRIVILEGE, loadOverview) }], { initialEntries: ['/'] })
  render(<AuthProvider getRequestedPath={() => '/'}><RouterProvider router={router} /></AuthProvider>)
}

it('shows counts and upcoming inspections with their actions', async () => {
  mount()
  expect(await screen.findByRole('heading', { name: 'Hallo Fischer' })).toBeTruthy()
  expect(screen.getByText('Vorschläge zu beantworten').previousElementSibling?.textContent).toBe('1')
  expect(screen.getByText('Offene Schäden').previousElementSibling?.textContent).toBe('–')
  expect(screen.getByText('Nachkontrollen').closest('a')?.getAttribute('href')).toBe('/inspections')
  expect(screen.getByText('Offene Schäden').closest('a')).toBeNull()

  const rows = screen.getAllByRole('row').slice(1)
  expect(rows.map((row) => within(row).getAllByRole('cell')[0].firstChild?.textContent)).toEqual(['4.12', '3.08', '3.10'])
  expect(within(rows[0]).getByText('Mi., 18.11. bis Fr., 20.11.2026')).toBeTruthy()
  expect(within(rows[0]).getByRole('link', { name: 'Kontrolle starten' }).getAttribute('href')).toBe('/inspections/7')
  expect(within(rows[1]).getByText('Nachkontrolle 1')).toBeTruthy()
  expect(within(rows[1]).getByText('bis Di., 24.11.2026')).toBeTruthy()
  expect((within(rows[2]).getByRole('button', { name: 'Beantworten' }) as HTMLButtonElement).disabled).toBe(true)
  expect(within(rows[2]).getByText('Fehlt noch: Beantworten')).toBeTruthy()
  expect(screen.getByText('Fehlt noch: Schadensmeldungen')).toBeTruthy()
})

it('shows who a proposal is waiting for instead of an action', async () => {
  overview.upcoming[2] = { ...overview.upcoming[2], canRespond: false }
  mount()
  const row = (await screen.findByText('3.10')).closest('tr') as HTMLElement
  expect(within(row).queryByRole('link')).toBeNull()
  expect(within(row).getByText('Wartet auf die zuständige Objektbetreuung')).toBeTruthy()
  overview.upcoming[2] = { ...overview.upcoming[2], canRespond: true }
})


it('keeps the tiles static without access to the inspection list', async () => {
  signedIn = { ...caretaker, privileges: ['INSPECTION_OVERVIEW_READ'] }
  mount()
  expect((await screen.findByText('Nachkontrollen')).closest('a')).toBeNull()
})
