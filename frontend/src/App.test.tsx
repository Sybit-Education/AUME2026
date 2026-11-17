// @vitest-environment jsdom
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import App from './App'
import i18n from './i18n'
import { UiError } from './errors'
import { api, login } from './auth'

vi.mock('./auth', () => ({
  initializeAuth: vi.fn(async () => ({ mode: 'dev' })),
  api: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}))

beforeEach(async () => {
  vi.clearAllMocks()
  await i18n.changeLanguage('de')
})
afterEach(() => { cleanup() })

it('switches UI language and reloads the greeting without signing in again', async () => {
  let signedIn = false
  vi.mocked(login).mockImplementation(async () => { signedIn = true })
  vi.mocked(api).mockImplementation(async (path) => {
    if (path === '/api/me') {
      return signedIn
        ? Response.json({ userKey: 'student', name: 'Alex', roles: ['STUDENT'], privileges: ['GREETING_READ'] })
        : new Response(null, { status: 401 })
    }
    return new Response(i18n.resolvedLanguage === 'en' ? 'Hello Alex, welcome to Sonne.' : 'Hallo Alex, willkommen in Sonne.')
  })
  render(<App />)
  const student = await screen.findByRole('button', { name: 'Studierende' })
  fireEvent.click(student)
  await screen.findByText('Hallo Alex, willkommen in Sonne.')
  fireEvent.change(screen.getByRole('combobox', { name: 'Sprache' }), { target: { value: 'en' } })
  await screen.findByText('Hello Alex, welcome to Sonne.')
  expect(screen.getByRole('button', { name: 'Sign out' })).toBeDefined()
  expect(document.documentElement.lang).toBe('en')
  expect(localStorage.getItem('aume.language')).toBe('en')
  expect(login).toHaveBeenCalledTimes(1)
})

it('translates API error keys and keeps the language picker usable after a failure', async () => {
  vi.mocked(api).mockRejectedValue(new UiError('errors.profile'))
  render(<App />)
  await screen.findByText('Das Nutzerprofil konnte nicht geladen werden.')
  await waitFor(() => { expect((screen.getByRole('combobox') as HTMLSelectElement).disabled).toBe(false) })
  fireEvent.change(screen.getByRole('combobox'), { target: { value: 'en' } })
  await screen.findByText('The user profile could not be loaded.')
  await waitFor(() => { expect((screen.getByRole('combobox') as HTMLSelectElement).disabled).toBe(false) })
})
