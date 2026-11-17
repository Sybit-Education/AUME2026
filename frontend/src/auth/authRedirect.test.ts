import { afterEach, beforeEach, expect, it, vi } from 'vitest'

const keycloak = vi.hoisted(() => ({
  login: vi.fn<(options: { redirectUri: string; locale?: string }) => Promise<void>>(),
  updateToken: vi.fn(async () => {}),
}))
vi.mock('keycloak-js', () => ({
  default: class {
    authenticated = true
    token = 'test-token'
    init = async () => true
    login = keycloak.login
    updateToken = keycloak.updateToken
  },
}))

beforeEach(() => {
  vi.resetModules()
  vi.resetAllMocks()
  vi.stubGlobal('window', { location: { origin: 'https://app.example.org' } })
  vi.stubGlobal('fetch', vi.fn(async () => Response.json({
    mode: 'keycloak', url: 'https://identity.example.org', realm: 'seezeit', clientId: 'seezeit-frontend',
  })))
})
afterEach(() => { vi.unstubAllGlobals() })

it('carries the full internal destination through a fragment-free OIDC callback', async () => {
  const { login } = await import('../auth')
  await login(undefined, '/residences?page=1#assigned')
  const callback = new URL(keycloak.login.mock.calls[0][0].redirectUri)
  expect(callback.origin).toBe('https://app.example.org')
  expect(callback.pathname).toBe('/login')
  expect(callback.hash).toBe('')
  expect(callback.searchParams.get('returnTo')).toBe('/residences?page=1#assigned')
})

it('does not put an external destination in the OIDC callback', async () => {
  const { login } = await import('../auth')
  await login(undefined, '//untrusted.example')
  expect(new URL(keycloak.login.mock.calls[0][0].redirectUri).searchParams.get('returnTo')).toBe('/')
})

it('reports failed token renewal as an expired session without calling the data API', async () => {
  keycloak.updateToken.mockRejectedValue(new Error('Token expired'))
  const { api } = await import('../auth')
  await expect(api('/api/residences')).rejects.toMatchObject({ key: 'errors.sessionExpired' })
  expect(fetch).toHaveBeenCalledTimes(1)
})
