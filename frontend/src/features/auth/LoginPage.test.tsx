// @vitest-environment jsdom
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import AuthProvider from '@/lib/auth/AuthProvider'
import { invalidateSession, refreshSession } from '@/lib/auth/session'
import i18n from '@/lib/i18n'
import { api, devLogin, login, logout, type LoggedInUser } from '@/lib/auth/client'
import LoginPage from './LoginPage'

vi.mock('@/lib/auth/client', () => ({
  initializeAuth: vi.fn(async () => ({ mode: 'dev' })),
  api: vi.fn(),
  login: vi.fn(),
  devLogin: vi.fn(),
  logout: vi.fn(),
}))

const student: LoggedInUser = { userKey: 'student', name: 'Alex', roles: ['STUDENT'], privileges: ['GREETING_READ'] }
let signedIn: LoggedInUser | null
let routers: ReturnType<typeof createMemoryRouter>[] = []

function mount() {
  const router = createMemoryRouter([
    { path: '/', element: <p>Start</p> },
    { path: '/login', Component: LoginPage },
  ], { initialEntries: ['/login'] })
  routers.push(router)
  render(<AuthProvider getRequestedPath={() => '/'}><RouterProvider router={router} /></AuthProvider>)
}

beforeEach(async () => {
  vi.resetAllMocks()
  invalidateSession()
  signedIn = null
  await i18n.changeLanguage('de')
  vi.mocked(login).mockImplementation(async (username) => { signedIn = username === 'student' ? student : null })
  vi.mocked(logout).mockResolvedValue()
  vi.mocked(api).mockImplementation(async (path) => {
    if (path === '/api/me') return signedIn ? Response.json(signedIn) : new Response(null, { status: 401 })
    throw new Error(`Unexpected API call: ${path}`)
  })
  await refreshSession()
})

afterEach(() => {
  cleanup()
  routers.forEach((router) => router.dispose())
  routers = []
})

it('signs in with the account name and password', async () => {
  mount()
  fireEvent.change(await screen.findByLabelText('Anmeldename'), { target: { value: 'student' } })
  fireEvent.change(screen.getByLabelText('Passwort'), { target: { value: 'secret' } })
  fireEvent.click(screen.getByRole('button', { name: 'Mit Seezeit-Konto anmelden' }))
  await waitFor(() => expect(login).toHaveBeenCalledWith('student', 'secret'))
})

it('shows the dev login under the account form only in dev mode', async () => {
  mount()
  const account = (await screen.findByRole('button', { name: 'Mit Seezeit-Konto anmelden' })).closest('section')!
  const devButton = screen.getByRole('button', { name: 'Dev-Login' })
  const dev = devButton.closest('section')!
  expect(dev).not.toBe(account)
  expect(account.compareDocumentPosition(dev) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
  expect(devButton.className).toBe(account.querySelector('button')!.className)
  expect(devButton.querySelector('svg')).not.toBeNull()
  const hint = screen.getByText('Dieser Dev-Login ist nur im Dev-Modus verfügbar.')
  expect(hint.classList.contains('login-dev-hint')).toBe(true)
  expect(dev.contains(hint)).toBe(true)
  fireEvent.change(screen.getByLabelText('Testnutzer'), { target: { value: 'clerk' } })
  fireEvent.click(devButton)
  await waitFor(() => expect(devLogin).toHaveBeenCalledWith('clerk'))
})

it('hides the dev login outside dev mode', async () => {
  const { initializeAuth } = await import('@/lib/auth/client')
  vi.mocked(initializeAuth).mockResolvedValue({ mode: 'keycloak', url: 'https://identity.example.org', realm: 'seezeit', clientId: 'seezeit-frontend' })
  invalidateSession()
  await refreshSession()
  mount()
  await screen.findByRole('button', { name: 'Mit Seezeit-Konto anmelden' })
  expect(screen.queryByRole('button', { name: 'Dev-Login' })).toBeNull()
  expect(screen.queryByText('Dieser Dev-Login ist nur im Dev-Modus verfügbar.')).toBeNull()
})

it('shows the account button in the selected language', async () => {
  mount()
  await screen.findByRole('button', { name: 'Mit Seezeit-Konto anmelden' })
  await i18n.changeLanguage('en')
  expect(screen.getByRole('button', { name: 'Sign in with Seezeit account' })).toBeDefined()
  expect(screen.getByLabelText('Username')).toBeDefined()
})
