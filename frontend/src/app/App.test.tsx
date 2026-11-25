// @vitest-environment jsdom
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { createMemoryRouter } from 'react-router'
import App from './App'
import { createAppRoutes } from './router'
import { invalidateSession } from '@/lib/auth/session'
import i18n from '@/lib/i18n'
import { UiError } from '@/lib/errors'
import { api, login, logout, type LoggedInUser } from '@/lib/auth/client'

vi.mock('@/lib/auth/client', () => ({
  initializeAuth: vi.fn(async () => ({ mode: 'dev' })),
  api: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}))

const student: LoggedInUser = { userKey: 'student', name: 'Alex', roles: ['STUDENT'], privileges: ['GREETING_READ', 'CHECKLIST_READ'] }
const clerk: LoggedInUser = { userKey: 'clerk', name: 'Sam', roles: ['CLERK'], privileges: ['RESIDENCE_READ'] }
const checklistDe = { version: 1, areas: [
  { name: 'Küche', criteria: [{ id: 1, title: 'Arbeitsflächen', description: 'Frei von Essensresten.' }] },
  { name: 'Bad', criteria: [{ id: 2, title: 'Dusche & WC', description: 'Hygienisch sauber.' }] },
] }
const checklistEn = { version: 1, areas: [
  { name: 'Kitchen', criteria: [{ id: 1, title: 'Work surfaces', description: 'Free of food leftovers.' }] },
  { name: 'Bathroom', criteria: [{ id: 2, title: 'Shower & toilet', description: 'Hygienically clean.' }] },
] }
let signedIn: LoggedInUser | null
let routers: ReturnType<typeof createMemoryRouter>[] = []

function mount(path = '/') {
  const router = createMemoryRouter(createAppRoutes(), { initialEntries: [path] })
  routers.push(router)
  render(<App router={router} />)
  return router
}

beforeEach(async () => {
  vi.resetAllMocks()
  invalidateSession()
  signedIn = null
  await i18n.changeLanguage('de')
  vi.mocked(login).mockImplementation(async (key) => { signedIn = key === 'student' ? student : clerk })
  vi.mocked(logout).mockImplementation(async () => { signedIn = null })
  vi.mocked(api).mockImplementation(async (path) => {
    if (path === '/api/me') return signedIn ? Response.json(signedIn) : new Response(null, { status: 401 })
    if (path === '/api/greeting') return new Response(i18n.resolvedLanguage === 'en' ? 'Hello Alex, welcome to Sonne.' : 'Hallo Alex, willkommen in Sonne.')
    if (path === '/api/student/inspection-appointment') return new Response(null, { status: 204 })
    if (path === '/api/student/inspection-results/latest') return new Response(null, { status: 204 })
    if (path === '/api/checklist') return Response.json(i18n.resolvedLanguage === 'en' ? checklistEn : checklistDe)
    if (path.startsWith('/api/residences?')) {
      const page = Number(new URL(path, 'http://localhost').searchParams.get('page'))
      return Response.json({
        content: [{ id: page + 1, name: page === 0 ? 'Sonne' : 'Mond', flats: [], caretaker: null, deputy: null }],
        page: { number: page, size: 20, totalPages: 2, totalElements: 21 },
      })
    }
    throw new Error(`Unexpected API call: ${path}`)
  })
})
afterEach(() => {
  cleanup()
  routers.forEach((router) => router.dispose())
  routers = []
})

it('restores the requested path, query and fragment after mock login', async () => {
  const router = mount('/residences?page=1#assigned')
  fireEvent.click(await screen.findByRole('button', { name: 'Sachbearbeitung' }))
  await screen.findByText('Mond')
  expect(router.state.location.pathname).toBe('/residences')
  expect(router.state.location.search).toBe('?page=1')
  expect(router.state.location.hash).toBe('#assigned')
  expect(login).toHaveBeenCalledWith('clerk', '/residences?page=1#assigned')
})

it('restores an existing session when opening a protected page directly', async () => {
  signedIn = clerk
  mount('/residences')
  await screen.findByText('Sonne')
  expect(login).not.toHaveBeenCalled()
})

it('does not call protected APIs before login or with a missing privilege', async () => {
  const router = mount('/residences')
  await screen.findByRole('button', { name: 'Studierende' })
  expect(vi.mocked(api).mock.calls.every(([path]) => path === '/api/me')).toBe(true)
  fireEvent.click(screen.getByRole('button', { name: 'Studierende' }))
  await screen.findByRole('heading', { name: 'Zugriff nicht erlaubt' })
  expect(router.state.location.pathname).toBe('/forbidden')
  expect(vi.mocked(api).mock.calls.some(([path]) => path.startsWith('/api/residences'))).toBe(false)
  expect(screen.queryByRole('link', { name: 'Wohnanlagen' })).toBeNull()
})

it('uses the query string for pagination and supports browser back navigation', async () => {
  signedIn = clerk
  const router = mount('/residences')
  await screen.findByText('Sonne')
  fireEvent.click(screen.getByRole('button', { name: 'Weiter' }))
  await screen.findByText('Mond')
  expect(router.state.location.search).toBe('?page=1')
  await act(async () => { await router.navigate(-1) })
  await screen.findByText('Sonne')
})

it('switches UI language and reloads the greeting without signing in again', async () => {
  signedIn = clerk
  mount()
  await screen.findByRole('heading', { name: 'Übersicht' })
  fireEvent.change(screen.getByRole('combobox', { name: 'Sprache' }), { target: { value: 'en' } })
  await screen.findByRole('heading', { name: 'Dashboard' })
  expect(screen.getByRole('button', { name: 'Sign out' })).toBeDefined()
  expect(document.documentElement.lang).toBe('en')
})

it('lets students switch the language under More, Settings, Language and keeps it after reloading', async () => {
  const router = mount()
  fireEvent.click(await screen.findByRole('button', { name: 'Studierende' }))
  await screen.findByText('Hallo Alex, willkommen in Sonne.')
  expect(screen.queryByRole('combobox', { name: 'Sprache' })).toBeNull()
  fireEvent.click(screen.getByRole('link', { name: 'Mehr' }))
  fireEvent.click(await screen.findByRole('link', { name: /Einstellungen/ }))
  expect(router.state.location.pathname).toBe('/student/settings')
  fireEvent.click(within(screen.getByRole('group', { name: 'Sprache' })).getByRole('button', { name: 'English' }))
  await screen.findByRole('heading', { name: 'Settings' })
  expect(screen.getByRole('button', { name: 'English' }).getAttribute('aria-pressed')).toBe('true')
  expect(screen.getByRole('link', { name: 'Home' })).toBeDefined()
  expect(document.documentElement.lang).toBe('en')
  expect(window.localStorage.getItem('aume.language')).toBe('en')
  fireEvent.click(screen.getByRole('link', { name: 'Home' }))
  await screen.findByText('Hello Alex, welcome to Sonne.')
  expect(login).toHaveBeenCalledTimes(1)
})

it('shows students the mobile tab bar with Start, Cleaning plan, Inspection and More and highlights the current tab', async () => {
  signedIn = student
  const router = mount()
  const tabs = await screen.findByRole('navigation', { name: 'Navigation der Studierenden' })
  expect(within(tabs).getAllByRole('link')).toHaveLength(4)
  for (const name of ['Start', 'Putzplan', 'Kontrolle', 'Mehr']) expect(within(tabs).getByRole('link', { name })).toBeDefined()
  expect(within(tabs).getByRole('link', { name: 'Start' }).getAttribute('aria-current')).toBe('page')
  expect(screen.queryByRole('navigation', { name: 'Hauptnavigation' })).toBeNull()
  fireEvent.click(within(tabs).getByRole('link', { name: 'Putzplan' }))
  await screen.findByRole('heading', { name: 'Kommt bald' })
  expect(router.state.location.pathname).toBe('/student/cleaning-plan')
  expect(within(tabs).getByRole('link', { name: 'Putzplan' }).getAttribute('aria-current')).toBe('page')
  fireEvent.click(within(tabs).getByRole('link', { name: 'Mehr' }))
  await screen.findByRole('heading', { name: 'Konto und Datenschutz' })
})

it('keeps the header navigation for staff and shows no student tab bar', async () => {
  signedIn = clerk
  mount()
  await screen.findByRole('navigation', { name: 'Hauptnavigation' })
  expect(screen.queryByRole('navigation', { name: 'Navigation der Studierenden' })).toBeNull()
})

it('shows the checklist by area, opens explanations and reloads it in English', async () => {
  signedIn = student
  const router = mount('/student/inspection')
  await screen.findByRole('heading', { name: 'Kontrolle' })
  expect(router.state.location.pathname).toBe('/student/inspection/appointment')
  const sections = within(screen.getByRole('navigation', { name: 'Kontrollbereich' }))
  fireEvent.click(sections.getByRole('link', { name: 'Checkliste' }))
  await screen.findByRole('heading', { name: 'Küche' })
  expect(router.state.location.pathname).toBe('/student/inspection/checklist')
  expect(screen.getByRole('link', { name: 'Kontrolle' }).getAttribute('aria-current')).toBe('page')
  expect(screen.getByRole('heading', { name: 'Bad' })).toBeDefined()
  expect(screen.getByText('Frei von Essensresten.', { exact: false }).closest('p')?.hidden).toBe(true)
  const details = screen.getByRole('button', { name: 'Details: Arbeitsflächen' })
  expect(details.getAttribute('aria-expanded')).toBe('false')
  fireEvent.click(details)
  expect(screen.getByRole('button', { name: 'Weniger: Arbeitsflächen' }).getAttribute('aria-expanded')).toBe('true')
  expect(screen.getByText('Frei von Essensresten.', { exact: false }).closest('p')?.hidden).toBe(false)
  expect(screen.queryByRole('heading', { name: 'Checkliste' })).toBeNull()
  expect(screen.queryByRole('group', { name: 'Sprache' })).toBeNull()
  await act(() => i18n.changeLanguage('en'))
  await screen.findByRole('heading', { name: 'Kitchen' })
  expect(screen.getByRole('button', { name: 'Details: Work surfaces' })).toBeDefined()
  const englishSections = within(screen.getByRole('navigation', { name: 'Inspection sections' }))
  for (const name of ['Appointment', 'Checklist', 'Result', 'History']) {
    expect(englishSections.getByRole('link', { name }).getAttribute('href')).toMatch(/^\/student\/inspection\//)
  }
})

it('keeps both control navigation levels active across all four sections', async () => {
  signedIn = student
  const router = mount()
  const bottom = within(await screen.findByRole('navigation', { name: 'Navigation der Studierenden' }))
  const control = bottom.getByRole('link', { name: 'Kontrolle' })
  expect(control.getAttribute('href')).toBe('/student/inspection/appointment')
  fireEvent.click(control)

  const top = within(await screen.findByRole('navigation', { name: 'Kontrollbereich' }))
  for (const [name, path] of [
    ['Termin', '/student/inspection/appointment'],
    ['Checkliste', '/student/inspection/checklist'],
    ['Ergebnis', '/student/inspection/result'],
    ['Verlauf', '/student/inspection/history'],
  ]) {
    fireEvent.click(top.getByRole('link', { name }))
    await waitFor(() => {
      expect(router.state.location.pathname).toBe(path)
      expect(top.getByRole('link', { name }).getAttribute('aria-current')).toBe('page')
      expect(control.getAttribute('aria-current')).toBe('page')
    })
    if (name === 'Ergebnis') {
      await screen.findByRole('heading', { name: 'Noch kein Ergebnis' })
    }
    if (name === 'Verlauf') {
      await screen.findByRole('heading', { name: 'Kommt bald' })
    }
  }
  expect(vi.mocked(api).mock.calls.some(([path]) => path.startsWith('/api/inspections'))).toBe(false)
})

it('goes back to the previous control section with the browser back button', async () => {
  signedIn = student
  const router = mount('/student/inspection/appointment')
  const top = within(await screen.findByRole('navigation', { name: 'Kontrollbereich' }))
  fireEvent.click(top.getByRole('link', { name: 'Checkliste' }))
  await screen.findByRole('heading', { name: 'Küche' })
  fireEvent.click(top.getByRole('link', { name: 'Verlauf' }))
  await waitFor(() => expect(router.state.location.pathname).toBe('/student/inspection/history'))
  await act(async () => { await router.navigate(-1) })
  await screen.findByRole('heading', { name: 'Küche' })
  expect(top.getByRole('link', { name: 'Checkliste' }).getAttribute('aria-current')).toBe('page')
})

it.each([
  ['/kontrolle', '/student/inspection/appointment'],
  ['/kontrolle/checkliste', '/student/inspection/checklist'],
  ['/kontrolle/verlauf', '/student/inspection/history'],
  ['/putzplan', '/student/cleaning-plan'],
  ['/mehr/einstellungen', '/student/settings'],
])('redirects the old student address %s to %s', async (oldPath, newPath) => {
  signedIn = student
  const router = mount(oldPath)
  await waitFor(() => expect(router.state.location.pathname).toBe(newPath))
})

it('keeps the bottom tab More highlighted on the settings page', async () => {
  signedIn = student
  mount('/student/settings')
  const bottom = within(await screen.findByRole('navigation', { name: 'Navigation der Studierenden' }))
  await screen.findByRole('heading', { name: 'Einstellungen' })
  expect(bottom.getByRole('link', { name: 'Mehr' }).getAttribute('aria-current')).toBe('page')
})

it('keeps the control index as the login destination and redirects to the appointment after sign-in', async () => {
  const warning = vi.spyOn(console, 'warn')
  try {
    const router = mount('/student/inspection')
    fireEvent.click(await screen.findByRole('button', { name: 'Studierende' }))
    await screen.findByRole('heading', { name: 'Kontrolle' })
    expect(login).toHaveBeenCalledWith('student', '/student/inspection')
    expect(router.state.location.pathname).toBe('/student/inspection/appointment')
    expect(warning).not.toHaveBeenCalledWith(expect.stringContaining('Matched leaf route at location "/student/inspection"'))
  } finally {
    warning.mockRestore()
  }
})

it('hides the checklist section without CHECKLIST_READ', async () => {
  signedIn = { ...student, privileges: ['GREETING_READ'] }
  mount('/student/inspection/result')
  const top = within(await screen.findByRole('navigation', { name: 'Kontrollbereich' }))
  expect(top.queryByRole('link', { name: 'Checkliste' })).toBeNull()
  expect(top.getByRole('link', { name: 'Ergebnis' }).getAttribute('aria-current')).toBe('page')
  expect(vi.mocked(api).mock.calls.some(([path]) => path === '/api/checklist')).toBe(false)
})

it.each(['CLERK', 'CARETAKER'])('keeps the student checklist closed to %s even with CHECKLIST_READ', async (role) => {
  signedIn = { ...clerk, roles: [role], privileges: ['CHECKLIST_READ'] }
  const router = mount('/student/inspection/checklist')

  await screen.findByRole('heading', { name: 'Zugriff nicht erlaubt' })
  expect(router.state.location.pathname).toBe('/forbidden')
  expect(vi.mocked(api).mock.calls.some(([path]) => path === '/api/checklist')).toBe(false)
})

it('shows an empty state when no checklist is published and hides it without the privilege', async () => {
  signedIn = student
  vi.mocked(api).mockImplementation(async (path) => path === '/api/me'
    ? Response.json(student) : path === '/api/checklist' ? new Response(null, { status: 404 }) : new Response('Hallo'))
  mount('/student/inspection/checklist')
  await screen.findByRole('heading', { name: 'Seezeit hat noch keine Checkliste veröffentlicht.' })
  cleanup()
  invalidateSession()
  vi.mocked(api).mockClear()
  signedIn = { ...student, privileges: ['GREETING_READ'] }
  vi.mocked(api).mockImplementation(async (path) => path === '/api/me' ? Response.json(signedIn) : new Response('Hallo'))
  mount('/student/inspection/checklist')
  await screen.findByRole('heading', { name: 'Zugriff nicht erlaubt' })
  expect(vi.mocked(api).mock.calls.some(([path]) => path === '/api/checklist')).toBe(false)
})

it('logs out on a protected page and requires login before showing its data again', async () => {
  signedIn = clerk
  const router = mount('/residences')
  await screen.findByText('Sonne')
  fireEvent.click(screen.getByRole('button', { name: 'Abmelden' }))
  await screen.findByRole('button', { name: 'Studierende' })
  expect(screen.queryByText('Sonne')).toBeNull()
  expect(router.state.location.pathname).toBe('/login')
  expect(logout).toHaveBeenCalledTimes(1)
})

it('returns an expired session to login while retaining the requested destination', async () => {
  signedIn = clerk
  vi.mocked(api).mockImplementation(async (path) => path === '/api/me'
    ? Response.json(clerk) : new Response(null, { status: 401 }))
  const router = mount('/residences?page=1')
  await screen.findByRole('button', { name: 'Studierende' })
  expect(router.state.location.pathname).toBe('/login')
  expect(new URLSearchParams(router.state.location.search).get('returnTo')).toBe('/residences?page=1')
})

it('keeps the pending destination when a session expires during navigation', async () => {
  signedIn = clerk
  const router = mount('/')
  await screen.findByRole('heading', { name: 'Übersicht' })
  vi.mocked(api).mockImplementation(async () => new Response(null, { status: 401 }))
  await act(async () => { await router.navigate('/residences?page=1#assigned') })
  await screen.findByRole('button', { name: 'Studierende' })
  expect(new URLSearchParams(router.state.location.search).get('returnTo')).toBe('/residences?page=1#assigned')
})

it('shows the forbidden page when the backend rejects an otherwise permitted action', async () => {
  signedIn = clerk
  vi.mocked(api).mockImplementation(async (path) => path === '/api/me'
    ? Response.json(clerk) : new Response(null, { status: 403 }))
  mount('/residences')
  await screen.findByRole('heading', { name: 'Zugriff nicht erlaubt' })
})

it('renders a translated not-found page for unknown URLs', async () => {
  mount('/does-not-exist')
  await screen.findByRole('heading', { name: 'Seite nicht gefunden' })
})

it('renders translated loader errors and keeps the language picker usable', async () => {
  vi.mocked(api).mockRejectedValue(new UiError('errors.profile'))
  mount()
  await screen.findByText('Das Nutzerprofil konnte nicht geladen werden.')
  fireEvent.change(screen.getByRole('combobox'), { target: { value: 'en' } })
  await screen.findByText('The user profile could not be loaded.')
  await waitFor(() => { expect((screen.getByRole('combobox') as HTMLSelectElement).disabled).toBe(false) })
})
