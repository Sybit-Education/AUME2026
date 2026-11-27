import { afterEach, beforeEach, expect, it, vi } from 'vitest'

function configResponse() {
  return Response.json({
    mode: 'keycloak', url: 'https://identity.example.org', realm: 'seezeit', clientId: 'seezeit-frontend',
  })
}

beforeEach(() => {
  vi.resetModules()
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
    if (String(input).endsWith('/api/auth/config')) return configResponse()
    return Response.json({ access_token: 'access', refresh_token: 'refresh', expires_in: 300 })
  }))
})
afterEach(() => { vi.unstubAllGlobals() })

it('sends the account name and password to the Keycloak token endpoint', async () => {
  const { login } = await import('./client')
  await login('sachbearbeiter', 'secret')
  const call = vi.mocked(fetch).mock.calls.find(([url]) => String(url).endsWith('/protocol/openid-connect/token'))
  const body = String(call?.[1]?.body)
  expect(String(call?.[0])).toBe('https://identity.example.org/realms/seezeit/protocol/openid-connect/token')
  expect(body).toContain('grant_type=password')
  expect(body).toContain('username=sachbearbeiter')
  expect(body).toContain('client_id=seezeit-frontend')
})

it('reports failed token renewal as an expired session without calling the data API', async () => {
  vi.mocked(fetch).mockImplementation(async (input: RequestInfo | URL) => {
    const target = String(input)
    if (target.endsWith('/api/auth/config')) return configResponse()
    if (target.endsWith('/protocol/openid-connect/token')) {
      return Response.json({ access_token: 'access', refresh_token: 'refresh', expires_in: 0 })
    }
    return new Response(null, { status: 500 })
  })
  const { login, api } = await import('./client')
  await login('student', 'secret')
  vi.mocked(fetch).mockClear()
  vi.mocked(fetch).mockImplementation(async (input: RequestInfo | URL) => {
    if (String(input).endsWith('/protocol/openid-connect/token')) return new Response(null, { status: 400 })
    return new Response('residences')
  })
  await expect(api('/api/residences')).rejects.toMatchObject({ key: 'errors.sessionExpired' })
  expect(vi.mocked(fetch).mock.calls.every(([url]) => String(url).endsWith('/protocol/openid-connect/token'))).toBe(true)
})
