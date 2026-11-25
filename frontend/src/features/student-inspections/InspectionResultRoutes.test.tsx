// @vitest-environment jsdom
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { cleanup, render, screen, within } from '@testing-library/react'
import { createMemoryRouter } from 'react-router'
// eslint-disable-next-line no-restricted-imports -- Exercise the real app in routing integration tests.
import App from '@/app/App'
// eslint-disable-next-line no-restricted-imports -- Use the production route tree in this test suite.
import { createAppRoutes } from '@/app/router'
import { invalidateSession } from '@/lib/auth/session'
import i18n from '@/lib/i18n'
import { api, login, logout, type LoggedInUser } from '@/lib/auth/client'
import type { StudentResult } from './api'

vi.mock('@/lib/auth/client', () => ({
  initializeAuth: vi.fn(async () => ({ mode: 'dev' })),
  api: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}))

const student: LoggedInUser = { userKey: 'student', name: 'Alex', roles: ['STUDENT'], privileges: ['GREETING_READ'] }
const RESULT_PATH = '/api/student/inspection-results/latest'
const APPOINTMENT_PATH = '/api/student/inspection-appointment'

const failed: StudentResult = {
  inspectionId: 7,
  kind: 'INITIAL',
  round: 0,
  inspectedAt: '2026-11-18T09:30:00Z',
  overallResult: 'FAILED',
  deficiencies: [
    { area: 'Küche', criterion: 'Herd & Backofen', note: 'Eingebrannte Fettreste.', buildingCauseSuspected: false },
    { area: 'Bad', criterion: 'Lüftung', note: null, buildingCauseSuspected: true },
  ],
  resolvedOnSite: [{ area: 'Bad', criterion: 'Dusche & WC', note: 'Abfluss gereinigt.' }],
  passed: [{ area: 'Küche', criterion: 'Arbeitsflächen' }, { area: 'Boden', criterion: 'Flurboden' }],
}
const passed: StudentResult = {
  ...failed,
  inspectionId: 8,
  kind: 'FOLLOW_UP',
  round: 1,
  inspectedAt: '2026-11-25T09:30:00Z',
  overallResult: 'PASSED',
  deficiencies: [],
  resolvedOnSite: [],
}

let result: StudentResult | null
let resultStatus: number
let appointment: { id: number; startsOn: string; endsOn: string } | null
let routers: ReturnType<typeof createMemoryRouter>[] = []

function mount(path = '/student/inspection/result') {
  const router = createMemoryRouter(createAppRoutes(), { initialEntries: [path] })
  routers.push(router)
  render(<App router={router} />)
  return router
}

const calls = (path: string) => vi.mocked(api).mock.calls.filter(([called]) => called === path)
const card = (heading: string | RegExp) => screen.getByRole('heading', { name: heading }).closest('section') as HTMLElement

beforeEach(async () => {
  vi.resetAllMocks()
  invalidateSession()
  result = null
  resultStatus = 204
  appointment = null
  await i18n.changeLanguage('de')
  vi.mocked(login).mockImplementation(async () => {})
  vi.mocked(logout).mockImplementation(async () => {})
  vi.mocked(api).mockImplementation(async (path) => {
    if (path === '/api/me') return Response.json(student)
    if (path === RESULT_PATH) return result ? Response.json(result) : new Response(null, { status: resultStatus })
    if (path === APPOINTMENT_PATH) return appointment ? Response.json(appointment) : new Response(null, { status: 204 })
    throw new Error(`Unexpected API call: ${path}`)
  })
})
afterEach(() => {
  cleanup()
  routers.forEach((router) => router.dispose())
  routers = []
})

it('shows the empty state with the announced inspection hint', async () => {
  appointment = { id: 3, startsOn: '2026-12-07', endsOn: '2026-12-11' }
  mount()
  await screen.findByRole('heading', { name: 'Noch kein Ergebnis' })
  expect(screen.getByText('Eure Kontrolle ist angekündigt. Das Ergebnis erscheint hier, sobald Seezeit es übermittelt hat. '
    + 'Das passiert spätestens 24 Stunden nach der Kontrolle.')).toBeDefined()
  expect(screen.queryByText('Nach der nächsten Kontrolle erscheint hier euer Ergebnis.')).toBeNull()
  const tabs = within(screen.getByRole('navigation', { name: 'Kontrollbereich' }))
  expect(tabs.getByRole('link', { name: 'Ergebnis' }).getAttribute('aria-current')).toBe('page')
})

it('shows the empty state without an announced inspection, also without an active flat (404)', async () => {
  mount()
  await screen.findByRole('heading', { name: 'Noch kein Ergebnis' })
  expect(screen.getByText('Nach der nächsten Kontrolle erscheint hier euer Ergebnis.')).toBeDefined()
  expect(calls(APPOINTMENT_PATH)).toHaveLength(1)
  cleanup()

  invalidateSession()
  resultStatus = 404
  mount()
  await screen.findByText('Nach der nächsten Kontrolle erscheint hier euer Ergebnis.')
})

it('shows a passed result without next step and without deficiencies', async () => {
  result = passed
  mount()
  await screen.findByRole('heading', { name: 'Ergebnis · Nachkontrolle am 25.11.2026' })
  expect(screen.getByText('Ergebnis verfügbar')).toBeDefined()
  expect(screen.getByText('Bestanden! Eure WG hat die Kontrolle gut gemeistert.')).toBeDefined()
  expect(screen.queryByText('Nicht bestanden')).toBeNull()
  expect(screen.getByText('Die Bewertung hat die Objektbetreuung vorgenommen. Nur eure WG sieht dieses Ergebnis.'))
    .toBeDefined()
  expect(screen.queryByRole('heading', { name: 'Euer nächster Schritt' })).toBeNull()
  expect(within(card('Mängel (0)')).getByText('Es wurden keine Mängel festgestellt.')).toBeDefined()
  expect(within(card('Bestandene Kriterien (2)')).getAllByText('in Ordnung')).toHaveLength(2)
  // Der Termin wird nur für den Leerzustand gebraucht.
  expect(calls(APPOINTMENT_PATH)).toHaveLength(0)
})

it('shows a failed result with next step, deficiencies and notes', async () => {
  result = failed
  mount()
  await screen.findByRole('heading', { name: 'Ergebnis · Erstkontrolle am 18.11.2026' })
  const failedText = screen.getByText('Nicht bestanden')
  expect(failedText.className).toContain('result-failed')
  expect(screen.queryByText('Bestanden! Eure WG hat die Kontrolle gut gemeistert.')).toBeNull()
  expect(within(card('Euer nächster Schritt')).getByText(
    'Behebt die Mängel. Seezeit legt die Frist für die Nachkontrolle fest und schickt euch eine Nachricht.')).toBeDefined()

  const deficiencies = within(card('Mängel (2)'))
  const stove = deficiencies.getByText('Küche · Herd & Backofen').closest('li') as HTMLElement
  expect(within(stove).getByText('Mangel')).toBeDefined()
  expect(within(stove).getByText('Eingebrannte Fettreste.')).toBeDefined()
  expect(within(card('Bestandene Kriterien (2)')).getByText('Arbeitsflächen')).toBeDefined()
  expect(screen.queryByRole('button', { name: /erledigt|Rückfrage/i })).toBeNull()
})

it('marks a suspected building cause instead of a deficiency chip and shows "Keine Notiz."', async () => {
  result = failed
  mount()
  await screen.findByRole('heading', { name: 'Mängel (2)' })
  const deficiencies = within(card('Mängel (2)'))
  const vent = deficiencies.getByText('Bad · Lüftung').closest('li') as HTMLElement
  expect(within(vent).getByText('Gebäudeursache wird geprüft')).toBeDefined()
  expect(within(vent).queryByText('Mangel')).toBeNull()
  expect(within(vent).getByText('Keine Notiz.')).toBeDefined()
  expect(within(vent).getByText('Die Objektbetreuung vermutet eine Ursache am Gebäude. Seezeit prüft das.')).toBeDefined()
})

it('shows "Vor Ort geklärt" only when something was resolved on site', async () => {
  result = failed
  mount()
  await screen.findByRole('heading', { name: 'Vor Ort geklärt (1)' })
  const resolved = within(card('Vor Ort geklärt (1)'))
  expect(resolved.getByText('Bad · Dusche & WC')).toBeDefined()
  expect(resolved.getByText('geklärt')).toBeDefined()
  cleanup()

  invalidateSession()
  result = passed
  mount()
  await screen.findByRole('heading', { name: 'Ergebnis · Nachkontrolle am 25.11.2026' })
  expect(screen.queryByRole('heading', { name: /Vor Ort geklärt/ })).toBeNull()
})

it('shows the result in English', async () => {
  await i18n.changeLanguage('en')
  result = failed
  mount()
  await screen.findByRole('heading', { name: 'Result · Initial inspection on Nov 18, 2026' })
  expect(screen.getByText('Not passed')).toBeDefined()
  expect(screen.getByRole('heading', { name: 'Your next step' })).toBeDefined()
  expect(screen.getByRole('heading', { name: 'Deficiencies (2)' })).toBeDefined()
  expect(screen.getByText('Building cause under review')).toBeDefined()
  expect(screen.getByRole('heading', { name: 'Resolved on site (1)' })).toBeDefined()
  expect(screen.getByRole('heading', { name: 'Passed criteria (2)' })).toBeDefined()
})

it('shows the route error when the result cannot be loaded', async () => {
  resultStatus = 500
  mount()
  await screen.findByText('Das Kontrollergebnis konnte nicht geladen werden.')
})
