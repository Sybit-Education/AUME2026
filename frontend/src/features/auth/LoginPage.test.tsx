// @vitest-environment jsdom
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import AuthProvider from '@/lib/auth/AuthProvider'
import { invalidateSession, refreshSession } from '@/lib/auth/session'
import i18n from '@/lib/i18n'
import { api, login, logout, type LoggedInUser } from '@/lib/auth/client'
import LoginPage from './LoginPage'

vi.mock('@/lib/auth/client', () => ({
  initializeAuth: vi.fn(async () => ({ mode: 'dev' })),
  api: vi.fn(),
  login: vi.fn(),
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
  vi.mocked(login).mockImplementation(async (key) => { signedIn = key === 'student' ? student : null })
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

it('signs in with a mock user', async () => {
  mount()
  fireEvent.click(await screen.findByRole('button', { name: 'Studierende' }))
  await waitFor(() => expect(login).toHaveBeenCalledWith('student', '/'))
})

it('keeps the email and password on the page in the dev profile', async () => {
  mount()
  const email = await screen.findByLabelText('E-Mail')
  const password = screen.getByLabelText('Passwort')
  fireEvent.change(email, { target: { value: 'alex@example.edu' } })
  fireEvent.change(password, { target: { value: 'secret' } })
  fireEvent.click(screen.getByRole('button', { name: 'Mit Seezeit-Konto anmelden' }))
  expect(email).toHaveProperty('value', 'alex@example.edu')
  expect(password).toHaveProperty('value', 'secret')
  expect(login).not.toHaveBeenCalled()
})

it('shows the account button in the selected language', async () => {
  mount()
  await screen.findByRole('button', { name: 'Mit Seezeit-Konto anmelden' })
  await i18n.changeLanguage('en')
  expect(screen.getByRole('button', { name: 'Sign in with Seezeit account' })).toBeDefined()
  expect(screen.getByLabelText('Email')).toBeDefined()
})
