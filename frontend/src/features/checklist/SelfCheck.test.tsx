// @vitest-environment jsdom
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { createMemoryRouter } from 'react-router'
// eslint-disable-next-line no-restricted-imports -- Exercise the real app in routing integration tests.
import App from '@/app/App'
// eslint-disable-next-line no-restricted-imports -- Use the production route tree in this test suite.
import { createAppRoutes } from '@/app/router'
import { invalidateSession } from '@/lib/auth/session'
import i18n from '@/lib/i18n'
import { api, type LoggedInUser } from '@/lib/auth/client'

vi.mock('@/lib/auth/client', () => ({
  initializeAuth: vi.fn(async () => ({ mode: 'dev' })),
  api: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}))

const resident: LoggedInUser = { userKey: 'student', name: 'Alex', roles: ['STUDENT'], privileges: ['CHECKLIST_READ', 'WG_MEMBERSHIP'] }
const checklist = { version: 1, areas: [
  { name: 'Küche', criteria: [
    { id: 1, title: 'Arbeitsflächen', description: 'Frei von Essensresten.' },
    { id: 2, title: 'Spüle', description: 'Ohne Geschirr.' },
  ] },
  { name: 'Bad', criteria: [
    { id: 3, title: 'Dusche & WC', description: 'Hygienisch sauber.' },
    { id: 4, title: 'Waschbecken', description: 'Ohne Kalk.' },
  ] },
] }
const selfCheckPath = '/api/wg/self-check'
let signedIn: LoggedInUser
let checked: number[]
let selfCheckStatus: number
let routers: ReturnType<typeof createMemoryRouter>[] = []

function mount(path = '/student/inspection/checklist') {
  const router = createMemoryRouter(createAppRoutes(), { initialEntries: [path] })
  routers.push(router)
  render(<App router={router} />)
  return router
}

const calls = (method: string, path: string) => vi.mocked(api).mock.calls
  .filter(([url, init]) => url === path && (init?.method ?? 'GET') === method)

beforeEach(async () => {
  vi.resetAllMocks()
  invalidateSession()
  signedIn = resident
  checked = [1]
  selfCheckStatus = 200
  await i18n.changeLanguage('de')
  vi.mocked(api).mockImplementation(async (path, init) => {
    const method = init?.method ?? 'GET'
    if (path === '/api/me') return Response.json(signedIn)
    if (path === '/api/checklist') return Response.json(checklist)
    if (path === selfCheckPath && method === 'GET') {
      if (selfCheckStatus === 404) return Response.json({ code: 'not_member' }, { status: 404 })
      if (selfCheckStatus !== 200) return new Response(null, { status: selfCheckStatus })
      return Response.json({ checkedCriterionIds: checked })
    }
    if (path === selfCheckPath && method === 'DELETE') {
      checked = []
      return new Response(null, { status: 204 })
    }
    const single = /^\/api\/wg\/self-check\/(\d+)$/.exec(String(path))
    if (single) {
      const id = Number(single[1])
      checked = method === 'PUT' ? [...new Set([...checked, id])] : checked.filter((value) => value !== id)
      return new Response(null, { status: 204 })
    }
    throw new Error(`Unexpected API call: ${method} ${path}`)
  })
})

afterEach(() => {
  cleanup()
  routers.forEach((router) => router.dispose())
  routers = []
})

it('shows the progress of the own flat and ticks criteria off and on again', async () => {
  mount()
  const card = within(await screen.findByRole('region', { name: 'Selbstcheck' }))
  expect(card.getByText(/nur für eure WG sichtbar und zählt nicht zur Bewertung/)).toBeDefined()
  expect(card.getByText('1 / 4 erledigt')).toBeDefined()
  expect(card.getByRole('progressbar').getAttribute('aria-valuenow')).toBe('25')

  const spuele = screen.getByRole('checkbox', { name: 'Erledigt: Spüle' }) as HTMLInputElement
  expect(spuele.checked).toBe(false)
  expect((screen.getByRole('checkbox', { name: 'Erledigt: Arbeitsflächen' }) as HTMLInputElement).checked).toBe(true)

  fireEvent.click(spuele)
  expect(card.getByText('2 / 4 erledigt')).toBeDefined()
  await waitFor(() => expect(calls('PUT', `${selfCheckPath}/2`)).toHaveLength(1))
  await waitFor(() => expect(calls('GET', selfCheckPath)).toHaveLength(2))
  expect(spuele.checked).toBe(true)
  expect(card.getByRole('progressbar').getAttribute('aria-valuenow')).toBe('50')

  fireEvent.click(screen.getByRole('checkbox', { name: 'Erledigt: Arbeitsflächen' }))
  await waitFor(() => expect(calls('DELETE', `${selfCheckPath}/1`)).toHaveLength(1))
  await waitFor(() => expect(card.getByText('1 / 4 erledigt')).toBeDefined())
})

it('resets all ticks after confirming', async () => {
  checked = [1, 3]
  mount()
  const card = within(await screen.findByRole('region', { name: 'Selbstcheck' }))
  expect(card.getByText('2 / 4 erledigt')).toBeDefined()

  fireEvent.click(card.getByRole('button', { name: 'Zurücksetzen' }))
  const sheet = within(screen.getByRole('dialog', { name: 'Selbstcheck zurücksetzen?' }))
  expect(sheet.getByText(/Alle Haken eurer WG werden entfernt/)).toBeDefined()
  fireEvent.click(sheet.getByRole('button', { name: 'Abbrechen' }))
  expect(screen.queryByRole('dialog')).toBeNull()
  expect(calls('DELETE', selfCheckPath)).toHaveLength(0)

  fireEvent.click(card.getByRole('button', { name: 'Zurücksetzen' }))
  fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' })
  expect(screen.queryByRole('dialog')).toBeNull()

  fireEvent.click(card.getByRole('button', { name: 'Zurücksetzen' }))
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Zurücksetzen' }))
  expect(screen.queryByRole('dialog')).toBeNull()
  await waitFor(() => expect(card.getByText('0 / 4 erledigt')).toBeDefined())
  expect(calls('DELETE', selfCheckPath)).toHaveLength(1)
  expect(card.getByRole('progressbar').getAttribute('aria-valuenow')).toBe('0')
  expect((card.getByRole('button', { name: 'Zurücksetzen' }) as HTMLButtonElement).disabled).toBe(true)
  expect(screen.getAllByRole('checkbox').every((box) => !(box as HTMLInputElement).checked)).toBe(true)
})

it('asks people without a flat to join and keeps the checklist read-only', async () => {
  selfCheckStatus = 404
  mount()
  const card = within(await screen.findByRole('region', { name: 'Selbstcheck' }))
  expect(card.getByRole('link', { name: 'WG beitreten' }).getAttribute('href')).toBe('/student/flat')
  expect(screen.queryAllByRole('checkbox')).toHaveLength(0)
  expect(screen.getByRole('button', { name: 'Details: Spüle' })).toBeDefined()
})

it('keeps the checklist readable when the self-check cannot be loaded', async () => {
  selfCheckStatus = 500
  mount()
  const card = within(await screen.findByRole('region', { name: 'Selbstcheck' }))
  expect(card.getByRole('alert').textContent).toBe('Der Selbstcheck konnte nicht geladen werden.')
  expect(screen.queryAllByRole('checkbox')).toHaveLength(0)
  expect(screen.getByRole('button', { name: 'Details: Dusche & WC' })).toBeDefined()
})

it('shows no self-check without WG rights', async () => {
  signedIn = { ...resident, privileges: ['CHECKLIST_READ'] }
  mount()
  await screen.findByRole('button', { name: 'Details: Spüle' })
  expect(screen.queryByRole('region', { name: 'Selbstcheck' })).toBeNull()
  expect(calls('GET', selfCheckPath)).toHaveLength(0)
})
