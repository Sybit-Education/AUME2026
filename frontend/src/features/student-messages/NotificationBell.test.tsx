// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { createMemoryRouter } from 'react-router'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
// eslint-disable-next-line no-restricted-imports -- Exercise the common header in the real app.
import App from '@/app/App'
// eslint-disable-next-line no-restricted-imports -- Use the production route tree in this test suite.
import { createAppRoutes } from '@/app/router'
import { api, logout, type LoggedInUser } from '@/lib/auth/client'
import { getSession, invalidateSession, refreshSession } from '@/lib/auth/session'
import i18n from '@/lib/i18n'
import { requestUnreadCountRefresh } from './unreadCountRefresh'

vi.mock('@/lib/auth/client', () => ({
  initializeAuth: vi.fn(async () => ({ mode: 'dev' })),
  api: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}))

const inbox = '/api/student/notifications'
const countPath = `${inbox}/unread-count`
const student: LoggedInUser = { userKey: 'student', name: 'Alex', roles: ['STUDENT'], privileges: [] }
let account: LoggedInUser | null
let countResponse: () => Response | Promise<Response>
let routers: ReturnType<typeof createMemoryRouter>[]

function mount(path = '/student/more') {
  const router = createMemoryRouter(createAppRoutes(), { initialEntries: [path] })
  routers.push(router)
  render(<App router={router} />)
  return router
}

function bell(name: string) {
  return screen.findByRole('link', { name })
}

const requests = () => vi.mocked(api).mock.calls.filter(([path]) => path === countPath)

beforeEach(async () => {
  vi.resetAllMocks()
  invalidateSession()
  await i18n.changeLanguage('de')
  account = student
  countResponse = () => Response.json({ count: 0 })
  routers = []
  vi.mocked(logout).mockImplementation(async () => { account = null })
  vi.mocked(api).mockImplementation(async (path) => {
    if (path === '/api/me') return account ? Response.json(account) : new Response(null, { status: 401 })
    if (path === countPath) return countResponse()
    if (path.startsWith(`${inbox}?page=`)) {
      return Response.json({ content: [], page: { size: 20, number: 0, totalPages: 0, totalElements: 0 } })
    }
    if (path === '/api/student/inspection-appointment?view=details') return new Response(null, { status: 204 })
    throw new Error(`Unexpected API call: ${path}`)
  })
})

afterEach(() => {
  cleanup()
  routers.forEach((router) => router.dispose())
  vi.restoreAllMocks()
  vi.useRealTimers()
})

it.each([
  [0, 'Nachrichten, 0 ungelesene Nachrichten', ''],
  [1, 'Nachrichten, 1 ungelesene Nachricht', '1'],
  [4, 'Nachrichten, 4 ungelesene Nachrichten', '4'],
  [100, 'Nachrichten, 100 ungelesene Nachrichten', '99+'],
] as const)('shows count %i with an accessible label', async (count, label, badge) => {
  countResponse = () => Response.json({ count })
  mount()
  expect((await bell(label)).textContent).toBe(badge)
})

it('translates the label in English and retains separate back and logout controls', async () => {
  await i18n.changeLanguage('en')
  countResponse = () => Response.json({ count: 1 })
  mount('/student/settings')
  const link = await bell('Messages, 1 unread message')
  const signOut = screen.getByRole('button', { name: 'Sign out' })
  expect(link.nextElementSibling).toBe(signOut)
  expect(screen.getByRole('link', { name: 'Back' }).getAttribute('href')).toBe('/student/more')
  expect(screen.getByRole('heading', { name: 'Settings', level: 1 })).toBeTruthy()
})

it.each(['/', '/student/inspection/appointment', '/student/settings', '/student/messages'])(
  'shows the bell in the common student header at %s', async (path) => {
    mount(path)
    expect((await bell('Nachrichten, 0 ungelesene Nachrichten')).closest('header')).toBeTruthy()
  },
)

it.each(['CLERK', 'CARETAKER'])('makes no count requests in the %s view', async (role) => {
  account = { ...student, roles: [role] }
  mount('/student/more')
  await screen.findByRole('button', { name: 'Abmelden' })
  expect(document.querySelector('.student-notification-bell')).toBeNull()
  expect(requests()).toHaveLength(0)
})

it('does not show or load the bell on the login page', async () => {
  account = null
  mount('/login')
  await screen.findByRole('button', { name: 'Studierende' })
  expect(document.querySelector('.student-notification-bell')).toBeNull()
  expect(requests()).toHaveLength(0)
})

it('opens the inbox without marking a message read and refreshes on navigation and app return', async () => {
  vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible')
  const router = mount()
  fireEvent.click(await bell('Nachrichten, 0 ungelesene Nachrichten'))
  await screen.findByText('Keine Nachrichten vorhanden.')
  expect(router.state.location.pathname).toBe('/student/messages')
  await waitFor(() => expect(requests()).toHaveLength(2))
  await act(async () => { document.dispatchEvent(new Event('visibilitychange')) })
  expect(requests()).toHaveLength(3)
  expect(vi.mocked(api).mock.calls.some(([path]) => path.endsWith('/read'))).toBe(false)
})

it('polls once per interval through navigation and stops after logout', async () => {
  vi.useFakeTimers()
  const advance = (ms: number) => act(async () => { await vi.advanceTimersByTimeAsync(ms) })
  const router = mount()
  await advance(0)
  expect(requests()).toHaveLength(1)
  await act(async () => { await router.navigate('/student/settings') })
  expect(requests()).toHaveLength(2)
  countResponse = () => Response.json({ count: 2 })
  await advance(14999)
  expect(requests()).toHaveLength(2)
  await advance(1)
  expect(requests()).toHaveLength(3)
  expect(screen.getByRole('link', { name: 'Nachrichten, 2 ungelesene Nachrichten' })).toBeTruthy()
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Abmelden' })) })
  await advance(30000)
  expect(requests()).toHaveLength(3)
  expect(document.querySelector('.student-notification-bell')).toBeNull()
  expect(logout).toHaveBeenCalledOnce()
})

it('preserves the last count on an error and ignores superseded results', async () => {
  vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible')
  countResponse = () => Response.json({ count: 5 })
  mount()
  await bell('Nachrichten, 5 ungelesene Nachrichten')
  countResponse = () => new Response(null, { status: 500 })
  await act(async () => { window.dispatchEvent(new Event('focus')) })
  expect(screen.getByRole('link', { name: 'Nachrichten, 5 ungelesene Nachrichten' })).toBeTruthy()

  let finish!: (response: Response) => void
  countResponse = () => new Promise((resolve) => { finish = resolve })
  window.dispatchEvent(new Event('focus'))
  window.dispatchEvent(new Event('focus'))
  expect(requests()).toHaveLength(3)
  const oldRequest = requests().at(-1)?.[1]?.signal
  countResponse = () => Response.json({ count: 4 })
  const user = getSession().user!
  await act(async () => { requestUnreadCountRefresh(user) })
  expect(oldRequest?.aborted).toBe(true)
  await bell('Nachrichten, 4 ungelesene Nachrichten')
  await act(async () => { finish(Response.json({ count: 5 })) })
  expect(screen.getByRole('link', { name: 'Nachrichten, 4 ungelesene Nachrichten' })).toBeTruthy()
})

it('drops the old count and aborts pending requests when the user changes', async () => {
  vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible')
  countResponse = () => Response.json({ count: 7 })
  mount()
  await bell('Nachrichten, 7 ungelesene Nachrichten')

  let finishOld!: (response: Response) => void
  countResponse = () => new Promise((resolve) => { finishOld = resolve })
  window.dispatchEvent(new Event('focus'))
  const oldRequest = requests().at(-1)?.[1]?.signal
  let finishNew!: (response: Response) => void
  account = { ...student, userKey: 'other-student', name: 'Sam' }
  countResponse = () => new Promise((resolve) => { finishNew = resolve })
  await act(async () => { await refreshSession() })
  expect(oldRequest?.aborted).toBe(true)
  expect(screen.getByRole('link', { name: 'Nachrichten' }).textContent).toBe('')
  await act(async () => { finishOld(Response.json({ count: 78 })) })
  expect(screen.getByRole('link', { name: 'Nachrichten' }).textContent).toBe('')
  await act(async () => { finishNew(Response.json({ count: 1 })) })
  expect(await bell('Nachrichten, 1 ungelesene Nachricht')).toBeTruthy()
})
