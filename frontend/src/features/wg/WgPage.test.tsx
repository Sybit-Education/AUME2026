// @vitest-environment jsdom
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react'
import { createMemoryRouter } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import AuthProvider from '@/lib/auth/AuthProvider'
import { api, type LoggedInUser } from '@/lib/auth/client'
import { ensureSession, invalidateSession } from '@/lib/auth/session'
import i18n from '@/lib/i18n'
import { wgRoutes } from './routes'

vi.mock('@/lib/auth/client', () => ({
  initializeAuth: vi.fn(async () => ({ mode: 'dev' })),
  api: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}))

const student: LoggedInUser = { userKey: 'student', name: 'Alex', roles: ['STUDENT'], privileges: ['WG_MEMBERSHIP'] }
const clerk: LoggedInUser = { userKey: 'clerk', name: 'Sam', roles: ['CLERK'], privileges: ['RESIDENCE_READ'] }
const joined = { flat: 'S-01', residence: 'Wohnanlage Sonne', validFrom: '2026-11-22', members: ['Alex', 'Bea'] }
let signedIn: LoggedInUser
let membership: typeof joined | null
let routers: ReturnType<typeof createMemoryRouter>[] = []

function mount() {
  const router = createMemoryRouter([{
    path: '/',
    loader: ensureSession,
    HydrateFallback: () => null,
    children: [...wgRoutes, { path: 'forbidden', element: <h1>forbidden</h1> }],
  }], { initialEntries: ['/wg'] })
  routers.push(router)
  render(<AuthProvider getRequestedPath={() => '/wg'}><RouterProvider router={router} /></AuthProvider>)
  return router
}

beforeEach(async () => {
  vi.resetAllMocks()
  invalidateSession()
  signedIn = student
  membership = null
  await i18n.changeLanguage('de')
  vi.mocked(api).mockImplementation(async (path, options) => {
    if (path === '/api/me') return Response.json(signedIn)
    if (path === '/api/memberships/me') return membership ? Response.json(membership) : new Response(null, { status: 204 })
    if (path === '/api/memberships' && options?.method === 'POST') {
      if (JSON.parse(String(options.body)).code !== 'GUT') return Response.json({ code: 'invalid_code' }, { status: 400 })
      membership = joined
      return Response.json(joined, { status: 201 })
    }
    if (path === '/api/memberships/me/leave' && options?.method === 'POST') {
      membership = null
      return new Response(null, { status: 204 })
    }
    throw new Error(`Unexpected API call: ${path}`)
  })
})

afterEach(() => {
  cleanup()
  routers.forEach((router) => router.dispose())
  routers = []
  vi.restoreAllMocks()
})

async function submitCode(code: string) {
  fireEvent.change(await screen.findByLabelText('Einladungscode'), { target: { value: code } })
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'WG beitreten' })) })
}

it('shows a translated error for an invalid code and stays on the form', async () => {
  mount()
  await submitCode('FALSCH')
  expect((await screen.findByRole('alert')).textContent).toBe('Dieser Code ist ungültig oder nicht mehr gültig.')
  expect(screen.getByLabelText('Einladungscode')).toBeTruthy()
})

it('joins with a valid code and shows the flat with its members', async () => {
  mount()
  await submitCode('GUT')
  expect(await screen.findByText('S-01')).toBeTruthy()
  expect(screen.getByText('Wohnanlage Sonne')).toBeTruthy()
  expect(screen.getByText('Bea')).toBeTruthy()
})

it('leaves only after confirming on the page, without a browser dialog', async () => {
  membership = joined
  const confirm = vi.spyOn(window, 'confirm')
  mount()
  fireEvent.click(await screen.findByRole('button', { name: 'WG verlassen' }))
  fireEvent.click(screen.getByRole('button', { name: 'Abbrechen' }))
  expect(membership).not.toBeNull()

  fireEvent.click(screen.getByRole('button', { name: 'WG verlassen' }))
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Ja, WG verlassen' })) })
  expect(await screen.findByLabelText('Einladungscode')).toBeTruthy()
  expect(membership).toBeNull()
  expect(confirm).not.toHaveBeenCalled()
})

it('sends users without the privilege to the forbidden page without loading flat data', async () => {
  signedIn = clerk
  mount()
  expect(await screen.findByRole('heading', { name: 'forbidden' })).toBeTruthy()
  expect(vi.mocked(api).mock.calls.some(([path]) => path === '/api/memberships/me')).toBe(false)
})
