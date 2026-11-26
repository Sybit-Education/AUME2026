// @vitest-environment jsdom
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { act, cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { createMemoryRouter } from 'react-router'
// eslint-disable-next-line no-restricted-imports -- Exercise the real app in routing integration tests.
import App from '@/app/App'
// eslint-disable-next-line no-restricted-imports -- Use the production route tree in this test suite.
import { createAppRoutes } from '@/app/router'
import { invalidateSession } from '@/lib/auth/session'
import i18n from '@/lib/i18n'
import { api, login, logout, type LoggedInUser } from '@/lib/auth/client'

vi.mock('@/lib/auth/client', () => ({
  initializeAuth: vi.fn(async () => ({ mode: 'dev' })),
  api: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}))

const student: LoggedInUser = {
  userKey: 'student', name: 'Alex', roles: ['STUDENT'], privileges: ['GREETING_READ', 'CHECKLIST_READ', 'INFO_PAGE_READ'],
}
const info = {
  process: '1. Ihr bekommt eine Ankündigung.\n2. Die Objektbetreuung prüft die Gemeinschaftsflächen.',
  consequences: 'Bei Mängeln gibt es eine Nachkontrolle. Die Kosten einer Reinigungsfirma trägt die WG.',
  standards: 'Die Standards gelten für alle Wohnanlagen gleich.',
  updatedAt: '2026-11-20T10:00:00Z',
}
let signedIn: LoggedInUser | null
let infoResponse: () => Response | Promise<Response>
let appointmentResponse: () => Response | Promise<Response>
let routers: ReturnType<typeof createMemoryRouter>[] = []

function mount(path: string) {
  const router = createMemoryRouter(createAppRoutes(), { initialEntries: [path] })
  routers.push(router)
  render(<App router={router} />)
  return router
}

const infoCalls = () => vi.mocked(api).mock.calls.filter(([path]) => path === '/api/info-pages')
const section = (name: string | RegExp) => screen.getByRole('button', { name })
const appointmentPath = '/student/inspection/appointment'
const processPath = '/student/inspection/process'

beforeEach(async () => {
  vi.resetAllMocks()
  invalidateSession()
  signedIn = student
  infoResponse = () => Response.json(info)
  appointmentResponse = () => new Response(null, { status: 204 })
  await i18n.changeLanguage('de')
  vi.mocked(login).mockImplementation(async () => { signedIn = student })
  vi.mocked(logout).mockImplementation(async () => { signedIn = null })
  vi.mocked(api).mockImplementation(async (path) => {
    if (path === '/api/me') return signedIn ? Response.json(signedIn) : new Response(null, { status: 401 })
    if (path === '/api/info-pages') return infoResponse()
    if (path === '/api/student/inspection-appointment?view=details') return appointmentResponse()
    if (path === '/api/checklist') return Response.json({ version: 1, areas: [] })
    throw new Error(`Unexpected API call: ${path}`)
  })
})
afterEach(() => {
  cleanup()
  routers.forEach((router) => router.dispose())
  routers = []
})

it('redirects an old process link to the embedded backend texts even without an appointment', async () => {
  const router = mount(processPath)
  await screen.findByRole('button', { name: /Was passiert bei Mängeln\?/ })
  expect(router.state.location.pathname).toBe(appointmentPath)
  expect(router.state.location.hash).toBe('#inspection-process')
  expect(await screen.findByText('Keine Kontrolle angekündigt.')).toBeTruthy()
  expect(infoCalls()).toHaveLength(1)

  const tabs = within(screen.getByRole('navigation', { name: 'Kontrollbereich' }))
  expect(tabs.getAllByRole('link')).toHaveLength(4)
  expect(tabs.getByRole('link', { name: 'Termin' }).getAttribute('aria-current')).toBe('page')
  expect(screen.getByRole('link', { name: 'Kontrolle' }).getAttribute('aria-current')).toBe('page')

  const infoCard = screen.getByRole('region', { name: 'Ablauf und Konsequenzen' })
  expect(infoCard.classList.contains('card')).toBe(true)
  expect(infoCard.querySelector('.card')).toBeNull()
  expect(within(infoCard).getByRole('heading', { name: 'Ablauf und Konsequenzen' })).toBeDefined()
  for (const name of [/So läuft die Kontrolle ab/, /Was passiert bei Mängeln\?/, /Sauberkeitsstandards/]) {
    expect(within(infoCard).getByRole('button', { name })).toBeDefined()
  }
  // Zeilenumbrüche bleiben als eigene Absätze erhalten.
  expect(screen.getByText('1. Ihr bekommt eine Ankündigung.', { exact: false }).closest('p')?.textContent)
    .not.toContain('2. Die Objektbetreuung')
})

it('does not show a second entry point or load information from the checklist', async () => {
  mount('/student/inspection/checklist')
  await screen.findByText('Diese Kriterien nutzt auch die Objektbetreuung bei der Kontrolle.')
  expect(screen.queryByRole('link', { name: /Ablauf und Konsequenzen/ })).toBeNull()
  expect(infoCalls()).toHaveLength(0)
})

it('highlights consequences: open by default, marked as important and costs emphasised', async () => {
  mount('/student/inspection/process')
  const consequences = await screen.findByRole('button', { name: /Was passiert bei Mängeln\?/ })
  expect(consequences.getAttribute('aria-expanded')).toBe('true')
  expect(within(consequences).getByText('Wichtig')).toBeDefined()
  expect(consequences.closest('section')?.className).toContain('important')
  const cost = screen.getByText('Die Kosten einer Reinigungsfirma trägt die WG.')
  expect(cost.tagName).toBe('STRONG')
  expect(screen.getByText('Bei Mängeln gibt es eine Nachkontrolle.', { exact: false }).tagName).not.toBe('STRONG')
})

it('expands and collapses a section with the green arrow header', async () => {
  mount('/student/inspection/process')
  const process = await screen.findByRole('button', { name: /So läuft die Kontrolle ab/ })
  const body = screen.getByText('1. Ihr bekommt eine Ankündigung.', { exact: false }).closest('[id]') as HTMLElement
  expect(process.getAttribute('aria-expanded')).toBe('false')
  expect(body.hidden).toBe(true)
  fireEvent.click(process)
  expect(process.getAttribute('aria-expanded')).toBe('true')
  expect(body.hidden).toBe(false)
  fireEvent.click(process)
  expect(body.hidden).toBe(true)
})

it('shows "Noch keine Informationen" for empty texts', async () => {
  infoResponse = () => Response.json({ process: '', consequences: '  ', standards: 'Gleich für alle.', updatedAt: null })
  mount('/student/inspection/process')
  await screen.findByRole('button', { name: /Was passiert bei Mängeln\?/ })
  fireEvent.click(section(/So läuft die Kontrolle ab/))
  expect(screen.getAllByText('Noch keine Informationen.')).toHaveLength(2)
  expect(screen.getByText('Gleich für alle.')).toBeDefined()
})

it('shows the page in English and notes that the texts are German only', async () => {
  await i18n.changeLanguage('en')
  mount('/student/inspection/process')
  await screen.findByRole('button', { name: /How the inspection works/ })
  for (const name of [/How the inspection works/, /What happens if issues are found\?/, /Cleanliness standards/]) {
    expect(section(name)).toBeDefined()
  }
  expect(within(section(/What happens if issues are found\?/)).getByText('Important')).toBeDefined()
  expect(screen.getByText('Seezeit provides these texts in German only.')).toBeDefined()
  expect(screen.queryByRole('link', { name: /Back to the checklist/ })).toBeNull()
})

it.each([403, 500])('keeps the appointment state when the info request returns %s', async (status) => {
  infoResponse = () => new Response(null, { status })
  mount(appointmentPath)
  await screen.findByText('Die Informationen konnten nicht geladen werden.')
  expect(await screen.findByText('Keine Kontrolle angekündigt.')).toBeTruthy()
})

it('shows the information while the appointment is pending and after it fails', async () => {
  let complete!: (response: Response) => void
  appointmentResponse = () => new Promise((resolve) => { complete = resolve })
  mount(appointmentPath)
  await screen.findByRole('button', { name: /Was passiert bei Mängeln\?/ })
  const card = within(screen.getByRole('region', { name: 'Kontrolltermin' }))
  expect(card.getByRole('status')).toBeTruthy()
  await act(async () => { complete(new Response(null, { status: 500 })) })
  await screen.findByText('Der Kontrolltermin konnte nicht geladen werden.')
  expect(screen.getByRole('button', { name: /Was passiert bei Mängeln\?/ })).toBeTruthy()
})

it('shows the appointment state while information is pending', async () => {
  let complete!: (response: Response) => void
  infoResponse = () => new Promise((resolve) => { complete = resolve })
  mount(appointmentPath)
  await screen.findByText('Keine Kontrolle angekündigt.')
  expect(screen.getByRole('status')).toBeTruthy()
  await act(async () => { complete(Response.json(info)) })
  await screen.findByRole('button', { name: /Was passiert bei Mängeln\?/ })
})

it('omits the information on the appointment page and blocks the old route without INFO_PAGE_READ', async () => {
  signedIn = { ...student, privileges: ['GREETING_READ', 'CHECKLIST_READ'] }
  mount(appointmentPath)
  await screen.findByText('Keine Kontrolle angekündigt.')
  expect(screen.queryByRole('heading', { name: 'Ablauf und Konsequenzen' })).toBeNull()
  cleanup()

  const router = mount('/student/inspection/process')
  await screen.findByRole('heading', { name: 'Zugriff nicht erlaubt' })
  expect(router.state.location.pathname).toBe('/forbidden')
  expect(infoCalls()).toHaveLength(0)
})

it('keeps the page closed to staff even with INFO_PAGE_READ', async () => {
  signedIn = { userKey: 'clerk', name: 'Sam', roles: ['CLERK'], privileges: ['INFO_PAGE_READ'] }
  const router = mount('/student/inspection/process')
  await screen.findByRole('heading', { name: 'Zugriff nicht erlaubt' })
  expect(router.state.location.pathname).toBe('/forbidden')
  expect(infoCalls()).toHaveLength(0)
})
