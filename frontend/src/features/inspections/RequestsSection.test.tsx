// @vitest-environment jsdom
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { createMemoryRouter } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import AuthProvider from '@/lib/auth/AuthProvider'
import { api, type LoggedInUser } from '@/lib/auth/client'
import { ensureSession, invalidateSession } from '@/lib/auth/session'
import i18n from '@/lib/i18n'
import type { InspectionRequest } from './requestsApi'
import { inspectionRoutes } from './routes'

vi.mock('@/lib/auth/client', () => ({
  initializeAuth: vi.fn(async () => ({ mode: 'dev' })),
  api: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}))

const caretaker: LoggedInUser = {
  userKey: 'caretaker', name: 'Kim', roles: ['CARETAKER'],
  privileges: ['RESIDENCE_READ', 'INSPECTION_PERFORM', 'INSPECTION_PERIOD_RESPOND'],
}
const clerk: LoggedInUser = { userKey: 'clerk', name: 'Sam', roles: ['CLERK'], privileges: ['INSPECTION_PLAN'] }
let signedIn: LoggedInUser
let requests: InspectionRequest[]
let writeResponse: (() => Response) | null
let routers: ReturnType<typeof createMemoryRouter>[] = []

function request(overrides: Partial<InspectionRequest> = {}): InspectionRequest {
  return {
    id: 7, residenceName: 'Sonne', kind: 'REGULAR', status: 'WAITING_FOR_CARETAKER', startsOn: '2026-12-01', endsOn: '2026-12-03',
    windowStart: '09:00:00', windowEnd: '16:00:00', flatCount: 2, flatLabels: null, counter: null, version: 3, ...overrides,
  }
}

function mount(path = '/inspections') {
  const router = createMemoryRouter([{
    path: '/',
    loader: ensureSession,
    HydrateFallback: () => null,
    children: [...inspectionRoutes, { path: 'forbidden', element: <h1>forbidden</h1> }],
  }], { initialEntries: [path] })
  routers.push(router)
  render(<AuthProvider getRequestedPath={() => path}><RouterProvider router={router} /></AuthProvider>)
  return router
}

const writes = () => vi.mocked(api).mock.calls.filter(([, init]) => init?.method === 'POST')
const body = (index = 0) => JSON.parse(String(writes()[index][1]?.body)) as Record<string, unknown>
const openList = async () => within(await screen.findByRole('table', { name: 'Anfragen' }))

beforeEach(async () => {
  vi.resetAllMocks()
  invalidateSession()
  signedIn = caretaker
  writeResponse = null
  requests = [request()]
  await i18n.changeLanguage('de')
  vi.mocked(api).mockImplementation(async (path, init) => {
    if (path === '/api/me') return Response.json(signedIn)
    if (init?.method === 'POST') {
      if (writeResponse) return writeResponse()
      const agreed = String(path).endsWith('/agree')
      requests = requests.map((item) => ({ ...item, status: agreed ? 'AGREED' : 'COUNTER_PROPOSED', version: item.version + 1 }))
      return Response.json(requests[0])
    }
    if (String(path).startsWith('/api/inspections?')) {
      return Response.json({ content: [], page: { number: 0, size: 100, totalElements: 0, totalPages: 0 } })
    }
    if (String(path).startsWith('/api/inspection-periods/assigned')) {
      return Response.json({ content: requests, page: { number: 0, size: 100, totalElements: requests.length, totalPages: 1 } })
    }
    throw new Error(`Unexpected API call: ${path}`)
  })
})

afterEach(() => {
  cleanup()
  routers.forEach((router) => router.dispose())
  routers = []
})

it('lists all requests with their status, open ones first and with actions', async () => {
  requests = [
    request(),
    request({ id: 8, residenceName: 'Egg', status: 'AGREED' }),
    request({
      id: 9, residenceName: 'Mond', status: 'COUNTER_PROPOSED',
      counter: { startsOn: '2026-12-08', endsOn: '2026-12-10', windowStart: null, windowEnd: null, reason: 'Urlaub' },
    }),
  ]
  mount()
  const list = await openList()
  const rows = list.getAllByRole('row').slice(1) as HTMLTableRowElement[]
  expect(rows.map((row) => row.cells[0].textContent)).toEqual(['Sonne', 'Mond', 'Egg'])
  expect(within(rows[0]).getByText('Offen')).toBeTruthy()
  expect(rows[0].cells[1].textContent).toBe('2')
  expect(rows[0].cells[2].textContent).toBe('Reguläre Kontrolle')
  expect(within(rows[0]).getByText('09:00 – 16:00')).toBeTruthy()
  expect(within(rows[0]).getByRole('link', { name: 'Vorschlag für Sonne beantworten' })).toBeTruthy()
  expect(within(rows[1]).getByText('Gegenvorschlag gesendet')).toBeTruthy()
  expect(within(rows[1]).getByText(/Dein Vorschlag/)).toBeTruthy()
  expect(within(rows[2]).getByText('Abgestimmt')).toBeTruthy()
  expect(within(rows[2]).queryByRole('link')).toBeNull()
  // Anfragen und anstehende Kontrollen stehen auf derselben Seite untereinander.
  expect(screen.getByRole('heading', { name: 'Anfragen' })).toBeTruthy()
  expect(screen.getByRole('heading', { name: 'Anstehende Kontrollen' })).toBeTruthy()
})

it('says so when there are no requests', async () => {
  requests = []
  mount()
  expect(await screen.findByText('Keine Anfragen.')).toBeTruthy()
})

it('agrees from the answer dialog', async () => {
  mount()
  fireEvent.click((await openList()).getByRole('link', { name: 'Vorschlag für Sonne beantworten' }))
  const dialog = within(await screen.findByRole('dialog', { name: 'Vorschlag · Sonne' }))
  expect(dialog.getByText(/09:00 – 16:00/)).toBeTruthy()
  fireEvent.click(dialog.getByRole('button', { name: 'Zustimmen' }))
  expect(await screen.findByRole('status')).toHaveProperty('textContent', 'Zugestimmt. Die Sachbearbeitung wird informiert.')
  expect(writes()[0][0]).toBe('/api/inspection-periods/7/agree')
  expect(body()).toEqual({ version: 3 })
  expect(screen.queryByRole('dialog')).toBeNull()
})

it('explains that a started period can only get a counter proposal', async () => {
  writeResponse = () => Response.json({ code: 'start_not_in_future' }, { status: 400 })
  mount('/inspections?answer=7')
  fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Zustimmen' }))
  expect((await screen.findByRole('alert')).textContent).toContain('Zustimmen geht nicht mehr')
})

it('sends a counter proposal with reason, prefilled from the proposal', async () => {
  mount()
  fireEvent.click((await openList()).getByRole('link', { name: 'Vorschlag für Sonne beantworten' }))
  fireEvent.click(within(await screen.findByRole('dialog')).getByRole('link', { name: 'Anderen Zeitraum vorschlagen' }))
  const form = within(await screen.findByRole('form', { name: 'Gegenvorschlag · Sonne' }))
  expect((form.getByLabelText('Zeitraum von') as HTMLInputElement).value).toBe('2026-12-01')
  expect(form.queryByLabelText('Uhrzeit von')).toBeNull()
  fireEvent.change(form.getByLabelText('Zeitraum bis'), { target: { value: '2026-12-05' } })
  fireEvent.change(form.getByLabelText(/Grund/), { target: { value: '  Urlaub  ' } })
  fireEvent.click(form.getByRole('button', { name: 'Gegenvorschlag senden' }))
  expect(await screen.findByRole('status')).toHaveProperty('textContent', 'Gegenvorschlag an die Sachbearbeitung gesendet.')
  expect(writes()[0][0]).toBe('/api/inspection-periods/7/counter')
  // Ohne Uhrzeit übernimmt das Backend die Uhrzeit aus dem Vorschlag.
  expect(body()).toEqual({
    startsOn: '2026-12-01', endsOn: '2026-12-05', windowStart: null, windowEnd: null, reason: 'Urlaub', version: 3,
  })
})

it('asks for a reason before sending anything', async () => {
  mount('/inspections?counter=7')
  const form = await screen.findByRole('form', { name: 'Gegenvorschlag · Sonne' })
  const reason = within(form).getByLabelText(/Grund/)
  fireEvent.change(reason, { target: { value: '   ' } })
  fireEvent.submit(form)
  // Kein Hinweis als Meldung, sondern direkt am Feld: rote Umrandung und Text darunter.
  expect(await within(form).findByText('Bitte gib einen Grund an.')).toBeTruthy()
  expect(reason.getAttribute('aria-invalid')).toBe('true')
  expect(screen.queryByRole('alert')).toBeNull()
  expect(writes()).toHaveLength(0)
})

it.each([
  ['a changed request', () => Response.json({ code: 'period_changed' }, { status: 409 }), 'Der Zeitraum wurde inzwischen geändert'],
  ['an already answered request', () => Response.json({ code: 'invalid_period_status' }, { status: 409 }), 'schon beantwortet'],
  ['a withdrawn request', () => new Response(null, { status: 404 }), 'gibt es nicht mehr'],
])('keeps the dialog open and explains %s', async (_, response, message) => {
  writeResponse = response
  mount('/inspections?answer=7')
  fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Zustimmen' }))
  expect((await screen.findByRole('alert')).textContent).toContain(message)
  expect(screen.getByRole('dialog')).toBeTruthy()
})

it('does not load the page without the respond privilege', async () => {
  signedIn = clerk
  mount()
  await screen.findByRole('heading', { name: 'forbidden' })
  expect(vi.mocked(api).mock.calls.some(([path]) => String(path).includes('/assigned'))).toBe(false)
})
