// @vitest-environment jsdom
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { createMemoryRouter } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import AuthProvider from '@/lib/auth/AuthProvider'
import { api, type LoggedInUser } from '@/lib/auth/client'
import { ensureSession, invalidateSession } from '@/lib/auth/session'
import i18n from '@/lib/i18n'
import { residenceRoutes } from './routes'

vi.mock('@/lib/auth/client', () => ({
  initializeAuth: vi.fn(async () => ({ mode: 'dev' })),
  api: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}))

const clerk: LoggedInUser = {
  userKey: 'clerk', name: 'Sam', roles: ['CLERK'], privileges: ['RESIDENCE_ASSIGN_STAFF', 'RESIDENCE_READ'],
}
const caretaker: LoggedInUser = { userKey: 'caretaker', name: 'Kim', roles: ['CARETAKER'], privileges: ['RESIDENCE_READ'] }
const bea = { id: 10, name: 'Bea' }
const cem = { id: 11, name: 'Cem' }
let signedIn: LoggedInUser | null
let routers: ReturnType<typeof createMemoryRouter>[] = []

// Features dürfen app/ nicht importieren. Der Test setzt deshalb nur die Feature-Routen mit der
// gemeinsamen Sitzungsprüfung zusammen; Anmeldung und Layout prüft app/App.test.tsx.
function mount(path: string) {
  const router = createMemoryRouter([{
    path: '/',
    loader: ensureSession,
    HydrateFallback: () => null,
    children: [...residenceRoutes, { path: 'forbidden', element: <h1>forbidden</h1> }],
  }], { initialEntries: [path] })
  routers.push(router)
  render(<AuthProvider getRequestedPath={() => path}><RouterProvider router={router} /></AuthProvider>)
  return router
}

function failPut(response: () => Response) {
  const fallback = vi.mocked(api).getMockImplementation()!
  vi.mocked(api).mockImplementation(async (path, options) => path === '/api/residences/1/staff' && options?.method === 'PUT'
    ? response() : fallback(path, options))
}

beforeEach(async () => {
  vi.resetAllMocks()
  invalidateSession()
  signedIn = clerk
  await i18n.changeLanguage('de')
  vi.mocked(api).mockImplementation(async (path, options) => {
    if (path === '/api/me') return signedIn ? Response.json(signedIn) : new Response(null, { status: 401 })
    if (path.startsWith('/api/residences?')) {
      const page = Number(new URL(path, 'http://localhost').searchParams.get('page'))
      return Response.json({
        content: [{
          id: page + 1, name: page === 0 ? 'Sonne' : 'Mond', flats: page === 0 ? ['S-01', 'S-02'] : [],
          caretaker: bea, deputy: page === 0 ? null : cem,
        }],
        page: { number: page, size: 20, totalPages: 2, totalElements: 21 },
      })
    }
    if (path.startsWith('/api/caretakers?')) {
      return Response.json({ content: [bea, cem], page: { number: 0, size: 100, totalPages: 1, totalElements: 2 } })
    }
    if (/^\/api\/residences\/\d+\/staff$/.test(path) && options?.method === 'PUT') return Response.json({})
    throw new Error(`Unexpected API call: ${path}`)
  })
})

afterEach(() => {
  cleanup()
  routers.forEach((router) => router.dispose())
  routers = []
})

it('shows flats, caretaker and deputy of each residence and marks missing values', async () => {
  mount('/residences')
  await screen.findByText('Sonne')
  expect(screen.getByText('S-01')).toBeDefined()
  expect(screen.getByText('S-02')).toBeDefined()
  expect(screen.getByText('Bea')).toBeDefined()
  expect(screen.getByText('Keine Vertretung')).toBeDefined()
  fireEvent.click(screen.getByRole('button', { name: 'Weiter' }))
  await screen.findByText('Mond')
  expect(screen.getByText('Cem')).toBeDefined()
  expect(screen.getByText('Keine WGs')).toBeDefined()
})

it('lets staff without the assign privilege read but not edit, and never loads the caretaker list', async () => {
  signedIn = caretaker
  mount('/residences?edit=1')
  await screen.findByText('Sonne')
  expect(screen.queryByRole('link', { name: /bearbeiten/i })).toBeNull()
  expect(screen.queryByRole('button', { name: 'Speichern' })).toBeNull()
  expect(vi.mocked(api).mock.calls.some(([path]) => path.startsWith('/api/caretakers'))).toBe(false)
})

it('edits caretaker and deputy through the URL, saves them and returns to the list', async () => {
  const router = mount('/residences')
  await screen.findByText('Sonne')
  fireEvent.click(screen.getByRole('link', { name: 'Zuständigkeit von Sonne bearbeiten' }))
  await screen.findByRole('button', { name: 'Speichern' })
  expect(router.state.location.search).toBe('?page=0&edit=1')
  fireEvent.change(screen.getByLabelText('Objektbetreuung'), { target: { value: '11' } })
  fireEvent.change(screen.getByLabelText('Vertretung'), { target: { value: '10' } })
  fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))
  await waitFor(() => { expect(router.state.location.search).toBe('?page=0') })
  expect(screen.queryByRole('button', { name: 'Speichern' })).toBeNull()
  const put = vi.mocked(api).mock.calls.find(([path]) => path === '/api/residences/1/staff')
  expect(put?.[1]?.method).toBe('PUT')
  expect(JSON.parse(put?.[1]?.body as string)).toEqual({ caretakerId: 11, deputyId: 10 })
})

it('removes the deputy when none is selected', async () => {
  const router = mount('/residences?page=1&edit=2')
  await screen.findByRole('button', { name: 'Speichern' })
  fireEvent.change(screen.getByLabelText('Vertretung'), { target: { value: '' } })
  fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))
  await waitFor(() => { expect(router.state.location.search).toBe('?page=1') })
  const put = vi.mocked(api).mock.calls.find(([path]) => path === '/api/residences/2/staff')
  expect(JSON.parse(put?.[1]?.body as string)).toEqual({ caretakerId: 10, deputyId: null })
})

it.each([
  ['deputy_must_differ', 'Betreuung und Vertretung müssen verschiedene Personen sein.'],
  ['unknown_caretaker', 'Die ausgewählte Person ist keine Objektbetreuung.'],
  ['caretaker_required', 'Bitte eine Objektbetreuung auswählen.'],
])('shows the translated message for the backend error %s and stays in edit mode', async (code, message) => {
  failPut(() => Response.json({ code }, { status: 400 }))
  const router = mount('/residences?edit=1')
  fireEvent.click(await screen.findByRole('button', { name: 'Speichern' }))
  expect((await screen.findByRole('alert')).textContent).toBe(message)
  expect(router.state.location.search).toBe('?page=0&edit=1')
  expect(screen.getByRole('button', { name: 'Speichern' })).toBeDefined()
})

it('uses a generic message for an unexpected save failure that follows a language switch', async () => {
  failPut(() => new Response(null, { status: 500 }))
  mount('/residences?edit=1')
  fireEvent.click(await screen.findByRole('button', { name: 'Speichern' }))
  await screen.findByText('Die Zuständigkeit konnte nicht gespeichert werden. Bitte erneut versuchen.')
  await act(async () => { await i18n.changeLanguage('en') })
  await screen.findByText('The responsibility could not be saved. Please try again.')
  expect(screen.getByLabelText('Caretaker')).toBeDefined()
})

it('goes to the forbidden page when the backend rejects the change', async () => {
  failPut(() => new Response(null, { status: 403 }))
  const router = mount('/residences?edit=1')
  fireEvent.click(await screen.findByRole('button', { name: 'Speichern' }))
  await waitFor(() => { expect(router.state.location.pathname).toBe('/forbidden') })
})
