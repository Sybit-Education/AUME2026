// @vitest-environment jsdom
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react'
import { createMemoryRouter } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import AuthProvider from '@/lib/auth/AuthProvider'
import { api, type LoggedInUser } from '@/lib/auth/client'
import { ensureSession, invalidateSession } from '@/lib/auth/session'
import i18n from '@/lib/i18n'
import { ENGLISH } from './api'
import { standardsRoutes } from './routes'

vi.mock('@/lib/auth/client', () => ({
  initializeAuth: vi.fn(async () => ({ mode: 'dev' })),
  api: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}))

const clerk: LoggedInUser = {
  userKey: 'clerk', name: 'Sam', roles: ['CLERK'], privileges: ['INFO_PAGE_EDIT', 'INFO_PAGE_READ', 'STANDARDS_VIEW'],
}
const clerkWithoutEditRight: LoggedInUser = {
  userKey: 'clerk-2', name: 'Kim', roles: ['CLERK'], privileges: ['INFO_PAGE_READ', 'STANDARDS_VIEW'],
}
const caretaker: LoggedInUser = { userKey: 'caretaker', name: 'Lou', roles: ['CARETAKER'], privileges: ['RESIDENCE_READ'] }
const student: LoggedInUser = { userKey: 'student', name: 'Alex', roles: ['STUDENT'], privileges: ['INFO_PAGE_READ'] }
let signedIn: LoggedInUser
let stored: { process: string; consequences: string; standards: string; updatedAt: string }
let putResponse: (() => Response) | null
let routers: ReturnType<typeof createMemoryRouter>[] = []

function mount() {
  const router = createMemoryRouter([{
    path: '/',
    loader: ensureSession,
    HydrateFallback: () => null,
    children: [...standardsRoutes, { path: 'forbidden', element: <h1>forbidden</h1> }],
  }], { initialEntries: ['/standards/info'] })
  routers.push(router)
  render(<AuthProvider getRequestedPath={() => '/standards/info'}><RouterProvider router={router} /></AuthProvider>)
  return router
}

const field = (label: string) => screen.findByLabelText<HTMLTextAreaElement>(label)
const putCalls = () => vi.mocked(api).mock.calls.filter(([path, options]) => path === '/api/info-pages' && options?.method === 'PUT')

beforeEach(async () => {
  vi.resetAllMocks()
  invalidateSession()
  signedIn = clerk
  putResponse = null
  stored = { process: 'Ablauf alt', consequences: 'Folgen alt', standards: 'Standards alt', updatedAt: '2026-11-21T10:00:00Z' }
  await i18n.changeLanguage('de')
  vi.mocked(api).mockImplementation(async (path, options) => {
    if (path === '/api/me') return Response.json(signedIn)
    if (path === '/api/info-pages' && options?.method === 'PUT') {
      if (putResponse) return putResponse()
      stored = { ...JSON.parse(String(options.body)), updatedAt: '2026-11-21T11:00:00Z' }
      return Response.json(stored)
    }
    if (path === '/api/info-pages') return Response.json(stored)
    throw new Error(`Unexpected API call: ${path}`)
  })
})

afterEach(() => {
  cleanup()
  routers.forEach((router) => router.dispose())
  routers = []
})

it('saves German and the fixed English version together and shows only German on the left', async () => {
  mount()
  expect((await screen.findAllByLabelText<HTMLTextAreaElement>('Englisch (fest hinterlegt)'))[0].value).toBe(ENGLISH.process)
  fireEvent.change(await field('Ablauf der Kontrolle'), { target: { value: 'Ablauf neu' } })
  fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))
  expect((await screen.findByRole('status')).textContent).toBe('Gespeichert. Bewohnende sehen die neuen Texte.')
  expect(JSON.parse(String(putCalls()[0][1]?.body))).toEqual({
    process: `Ablauf neu\n\n${ENGLISH.process}`,
    consequences: `Folgen alt\n\n${ENGLISH.consequences}`,
    standards: `Standards alt\n\n${ENGLISH.standards}`,
  })
  expect((await field('Ablauf der Kontrolle')).value).toBe('Ablauf neu')
})

it('shows the texts read-only without the edit privilege', async () => {
  signedIn = clerkWithoutEditRight
  mount()
  expect((await field('Folgen bei Mängeln')).readOnly).toBe(true)
  expect(screen.queryByRole('button', { name: 'Speichern' })).toBeNull()
  expect(putCalls()).toHaveLength(0)
})

it.each([student, caretaker])('does not load the page for $userKey', async (user) => {
  signedIn = user
  mount()
  await screen.findByRole('heading', { name: 'forbidden' })
  expect(vi.mocked(api).mock.calls.some(([path]) => path === '/api/info-pages')).toBe(false)
})

it('keeps the input and shows a translated error when the backend rejects it', async () => {
  putResponse = () => Response.json({ code: 'info_text_required' }, { status: 400 })
  mount()
  fireEvent.change(await field('Sauberkeitsstandards'), { target: { value: '   ' } })
  fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))
  expect((await screen.findByRole('alert')).textContent).toBe('Bitte alle drei Texte ausfüllen.')
  expect((await field('Sauberkeitsstandards')).value).toBe('   ')
})

it('keeps unsaved input when a language change reloads the route', async () => {
  const router = mount()
  fireEvent.change(await field('Ablauf der Kontrolle'), { target: { value: 'Noch nicht gespeichert' } })
  await act(async () => {
    await i18n.changeLanguage('en')
    await router.revalidate()
  })
  expect((await field('Inspection process')).value).toBe('Noch nicht gespeichert')
})
