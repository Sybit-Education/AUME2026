// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { createMemoryRouter } from 'react-router'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
// eslint-disable-next-line no-restricted-imports -- Exercise the real app in routing integration tests.
import App from '@/app/App'
// eslint-disable-next-line no-restricted-imports -- Use the production route tree in this test suite.
import { createAppRoutes } from '@/app/router'
import { api, login, type LoggedInUser } from '@/lib/auth/client'
import { invalidateSession } from '@/lib/auth/session'
import i18n from '@/lib/i18n'
import type { StudentMessage } from './api'

vi.mock('@/lib/auth/client', () => ({
  initializeAuth: vi.fn(async () => ({ mode: 'dev' })),
  api: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}))

const route = '/student/messages'
const endpoint = '/api/student/notifications'
const student: LoggedInUser = {
  userKey: 'student', name: 'Alex', roles: ['STUDENT'], privileges: ['CHECKLIST_READ', 'WG_MEMBERSHIP'],
}
const announcement: StudentMessage = {
  id: 11, type: 'ANNOUNCEMENT', createdAt: '2026-11-28T00:01:00Z', occurredAt: '2026-11-27T23:30:00Z',
  readAt: null, flatLabel: 'S-01', startsOn: '2026-12-07', endsOn: '2026-12-11',
  windowStart: '09:00', windowEnd: '16:00', hasChecklistPdf: true,
}
const week: StudentMessage = { ...announcement, id: 12, type: 'REMINDER_7_DAYS', hasChecklistPdf: false }
const tomorrow: StudentMessage = { ...week, id: 13, type: 'REMINDER_1_DAY' }
const readCalls = () => vi.mocked(api).mock.calls.filter(([path, options]) =>
  path.endsWith('/read') && options?.method === 'PUT')

let account: LoggedInUser | null
let pages: StudentMessage[][]
let readResponse: () => Response | Promise<Response>
let pdfResponse: () => Response
let listStatus: number
let routers: ReturnType<typeof createMemoryRouter>[]

function mount(path = route) {
  const router = createMemoryRouter(createAppRoutes(), { initialEntries: [path] })
  routers.push(router)
  render(<App router={router} />)
  return router
}

beforeEach(async () => {
  vi.resetAllMocks()
  invalidateSession()
  await i18n.changeLanguage('de')
  account = student
  pages = [[announcement, week, tomorrow]]
  readResponse = () => new Response(null, { status: 204 })
  pdfResponse = () => new Response('PDF', { headers: { 'Content-Type': 'application/pdf' } })
  listStatus = 200
  routers = []
  vi.mocked(login).mockImplementation(async () => { account = student })
  vi.mocked(api).mockImplementation(async (path) => {
    if (path === '/api/me') return account ? Response.json(account) : new Response(null, { status: 401 })
    if (path === `${endpoint}/unread-count`) {
      return Response.json({ count: pages.flat().filter((message) => message.readAt === null).length })
    }
    if (path.startsWith(`${endpoint}?page=`)) {
      if (listStatus !== 200) return new Response(null, { status: listStatus })
      const number = Number(new URL(path, 'http://localhost').searchParams.get('page'))
      return Response.json({
        content: pages[number] ?? [],
        page: { size: 20, number, totalPages: pages.length, totalElements: pages.flat().length },
      })
    }
    if (path.endsWith('/read')) {
      const response = await readResponse()
      if (response.ok) {
        const id = Number(path.split('/').at(-2))
        pages = pages.map((page) => page.map((message) =>
          message.id === id ? { ...message, readAt: '2026-11-28T08:00:00Z' } : message))
      }
      return response
    }
    if (path.endsWith('/checklist.pdf')) return pdfResponse()
    throw new Error(`Unexpected API call: ${path}`)
  })
})

afterEach(() => {
  cleanup()
  routers.forEach((router) => router.dispose())
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

it('links to messages above My Flat, keeps More active and provides a back link', async () => {
  const router = mount('/student/more')
  const living = within(await screen.findByRole('region', { name: 'Wohnen' }))
  expect(screen.getAllByRole('heading', { level: 3 }).map((heading) => heading.textContent))
    .toEqual(['Wohnen', 'Konto und Datenschutz'])
  const entry = living.getByRole('link', { name: /Nachrichten.*Ankündigungen und Erinnerungen/ })
  expect(living.getAllByRole('link')).toEqual([entry, living.getByRole('link', { name: /Meine WG/ })])
  expect(entry.getAttribute('href')).toBe(route)
  fireEvent.click(entry)

  await screen.findByRole('heading', { name: 'Nachrichten', level: 1 })
  expect(router.state.location.pathname).toBe(route)
  expect(screen.getByRole('link', { name: 'Mehr' }).getAttribute('aria-current')).toBe('page')
  expect(screen.getByRole('link', { name: 'Zurück' }).getAttribute('href')).toBe('/student/more')
  expect(screen.getByRole('button', { name: 'Abmelden' })).toBeTruthy()
})

it('returns to the requested page after login without making a request while signed out', async () => {
  account = null
  const router = mount(`${route}?page=1`)
  const signIn = await screen.findByRole('button', { name: 'Studierende' })
  expect(vi.mocked(api).mock.calls.every(([path]) => path === '/api/me')).toBe(true)
  fireEvent.click(signIn)
  await screen.findByRole('heading', { name: 'Nachrichten', level: 1 })
  expect(router.state.location.search).toBe('?page=1')
})

it.each(['CLERK', 'CARETAKER'])('blocks direct access for %s before calling the API', async (role) => {
  account = { ...student, roles: [role] }
  const router = mount()
  await screen.findByRole('heading', { name: 'Zugriff nicht erlaubt' })
  expect(router.state.location.pathname).toBe('/forbidden')
  expect(vi.mocked(api).mock.calls.every(([path]) => path === '/api/me')).toBe(true)
})

it('only marks a message after intentional opening and prevents duplicate requests while pending', async () => {
  let complete!: (response: Response) => void
  readResponse = () => new Promise((resolve) => { complete = resolve })
  mount()
  const head = await screen.findByRole('button', { name: /Sauberkeitskontrolle/ })
  await screen.findByRole('link', { name: 'Nachrichten, 3 ungelesene Nachrichten' })
  expect(head.textContent).toContain('28.11.2026')
  expect(head.textContent).toContain('Neu')
  expect(head.getAttribute('aria-expanded')).toBe('false')
  expect(screen.queryByRole('button', { name: /herunterladen/ })).toBeNull()
  expect(readCalls()).toHaveLength(0)

  fireEvent.click(head)
  await waitFor(() => expect(readCalls()).toHaveLength(1))
  expect(readCalls()[0][0]).toBe(`${endpoint}/11/read`)
  expect(head.getAttribute('aria-expanded')).toBe('true')
  expect(screen.getByText(/Die Sauberkeitskontrolle für WG S-01/)).toBeTruthy()
  expect(screen.getByText('Die Objektbetreuung kommt zwischen 09:00 und 16:00 Uhr.')).toBeTruthy()
  expect(head.textContent).toContain('Neu')
  fireEvent.click(head)
  fireEvent.click(head)
  expect(readCalls()).toHaveLength(1)

  await act(async () => { complete(new Response(null, { status: 204 })) })
  await waitFor(() => expect(head.textContent).not.toContain('Neu'))
  expect(await screen.findByRole('link', { name: 'Nachrichten, 2 ungelesene Nachrichten' })).toBeTruthy()
  fireEvent.click(head)
  fireEvent.click(head)
  expect(readCalls()).toHaveLength(1)
})

it('keeps the unread badge on failure and allows a retry', async () => {
  readResponse = () => new Response(null, { status: 500 })
  mount()
  const head = await screen.findByRole('button', { name: /Sauberkeitskontrolle/ })
  fireEvent.click(head)
  await screen.findByText('Die Nachricht konnte nicht als gelesen markiert werden.')
  expect(head.textContent).toContain('Neu')
  readResponse = () => new Response(null, { status: 204 })
  fireEvent.click(screen.getByRole('button', { name: 'Erneut als gelesen markieren' }))
  await waitFor(() => expect(head.textContent).not.toContain('Neu'))
  expect(readCalls()).toHaveLength(2)
})

it('uses pagination from the backend without marking closed messages as read', async () => {
  pages = [[announcement], [tomorrow]]
  const router = mount()
  fireEvent.click(await screen.findByRole('link', { name: 'Nächste Seite' }))
  await screen.findByRole('button', { name: /Kontrollzeitraum beginnt morgen/ })
  expect(router.state.location.search).toBe('?page=1')
  expect(readCalls()).toHaveLength(0)
  fireEvent.click(screen.getByRole('link', { name: 'Vorherige Seite' }))
  await screen.findByRole('button', { name: /Sauberkeitskontrolle/ })
  expect(readCalls()).toHaveLength(0)
})

it('does not apply a pending read to a message on another page', async () => {
  pages = [[announcement], [week]]
  let complete!: (response: Response) => void
  readResponse = () => new Promise((resolve) => { complete = resolve })
  mount()
  fireEvent.click(await screen.findByRole('button', { name: /Sauberkeitskontrolle/ }))
  await waitFor(() => expect(readCalls()).toHaveLength(1))
  fireEvent.click(screen.getByRole('link', { name: 'Nächste Seite' }))
  const next = await screen.findByRole('button', { name: /Kontrollzeitraum beginnt in 7 Tagen/ })
  await act(async () => { complete(new Response(null, { status: 204 })) })
  expect(next.textContent).toContain('Neu')
  expect(readCalls()).toHaveLength(1)
})

it('shows actionable checklist links on reminders and never a PDF', async () => {
  pages = [[{ ...week, hasChecklistPdf: true }, tomorrow]]
  mount()
  for (const title of [/Kontrollzeitraum beginnt in 7 Tagen/, /Kontrollzeitraum beginnt morgen/]) {
    const head = await screen.findByRole('button', { name: title })
    fireEvent.click(head)
    const card = within(head.closest('article')!)
    expect(card.getByRole('link', { name: 'Zur Checkliste' }).getAttribute('href')).toBe('/student/inspection/checklist')
    expect(card.queryByRole('button', { name: /herunterladen/ })).toBeNull()
  }
})

it('omits the attachment, incomplete time window and the checklist link when unavailable', async () => {
  account = { ...student, privileges: [] }
  pages = [[{ ...announcement, hasChecklistPdf: false, windowEnd: null }, tomorrow]]
  mount()
  fireEvent.click(await screen.findByRole('button', { name: /Sauberkeitskontrolle/ }))
  expect(screen.queryByText(/liegt als Anhang bei/)).toBeNull()
  expect(screen.queryByText(/Objektbetreuung kommt zwischen/)).toBeNull()
  expect(screen.queryByRole('button', { name: /herunterladen/ })).toBeNull()
  fireEvent.click(screen.getByRole('button', { name: /Kontrollzeitraum beginnt morgen/ }))
  expect(screen.queryByRole('link', { name: 'Zur Checkliste' })).toBeNull()
})

it('formats a year boundary and translates both reminder types in English', async () => {
  await i18n.changeLanguage('en')
  pages = [[{ ...announcement, startsOn: '2026-12-31', endsOn: '2027-01-02' }, week, tomorrow]]
  mount()
  await screen.findByRole('button', { name: /Cleanliness inspection 31 Dec 2026 to 02 Jan 2027/ })
  const reminder = screen.getByRole('button', { name: /The inspection period starts in 7 days/ })
  expect(screen.getByRole('button', { name: /The inspection period starts tomorrow/ })).toBeTruthy()
  fireEvent.click(reminder)
  expect(within(reminder.closest('article')!).getByRole('link', { name: 'Open checklist' })).toBeTruthy()
})

it.each([
  ['de', 'Checkliste_Sauberkeit.pdf', /Sauberkeitskontrolle/],
  ['en', 'Cleanliness_Checklist.pdf', /Cleanliness inspection/],
] as const)('downloads the attachment with the %s filename', async (language, file, title) => {
  await i18n.changeLanguage(language)
  pages = [[{ ...announcement, readAt: '2026-11-28T08:00:00Z' }]]
  pdfResponse = () => new Response('PDF', { headers: {
    'Content-Type': 'application/pdf', 'Content-Disposition': `attachment; filename="${file}"`,
  } })
  vi.stubGlobal('URL', class extends URL {
    static createObjectURL = vi.fn(() => 'blob:checklist')
    static revokeObjectURL = vi.fn()
  })
  const save = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
    expect(this.download).toBe(file)
  })
  mount()
  fireEvent.click(await screen.findByRole('button', { name: title }))
  fireEvent.click(screen.getByText(file))
  await waitFor(() => expect(save).toHaveBeenCalledOnce())
  expect(vi.mocked(api).mock.calls.some(([path]) => path === `${endpoint}/11/checklist.pdf`)).toBe(true)
  expect(readCalls()).toHaveLength(0)
})

it('reports a download failure on the open card and allows another attempt', async () => {
  pdfResponse = () => new Response(null, { status: 500 })
  mount()
  const head = await screen.findByRole('button', { name: /Sauberkeitskontrolle/ })
  fireEvent.click(head)
  const file = screen.getByRole('button', { name: 'Checkliste_Sauberkeit.pdf herunterladen' })
  fireEvent.click(file)
  const card = within(head.closest('article')!)
  await card.findByText('Die Checkliste konnte nicht heruntergeladen werden.')
  expect((file as HTMLButtonElement).disabled).toBe(false)
  expect(head.getAttribute('aria-expanded')).toBe('true')
})

it('shows a usable empty state', async () => {
  pages = [[]]
  mount()
  expect(await screen.findByText('Keine Nachrichten vorhanden.')).toBeTruthy()
})

it('shows the translated loading error', async () => {
  listStatus = 500
  mount()
  expect(await screen.findByText('Die Nachrichten konnten nicht geladen werden.')).toBeTruthy()
})
