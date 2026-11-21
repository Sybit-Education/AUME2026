// @vitest-environment jsdom
import { beforeEach, expect, it, vi } from 'vitest'
import { setOfflineModeEnabled } from '@/lib/offline'

beforeEach(() => {
  window.localStorage.clear()
  vi.resetModules()
  vi.unstubAllGlobals()
})

it('reuses cached user data when the API becomes unreachable in offline mode', async () => {
  const fetchMock = vi.fn()
    .mockResolvedValueOnce(Response.json({ mode: 'dev' }))
    .mockResolvedValueOnce(Response.json({ userKey: 'caretaker', name: 'Casey' }))
  vi.stubGlobal('fetch', fetchMock)

  const client = await import('./client')
  await client.initializeAuth()
  setOfflineModeEnabled(true)
  await client.api('/api/me')

  fetchMock.mockRejectedValueOnce(new TypeError('Network unavailable'))
  const cached = await client.api('/api/me')

  expect(await cached.json()).toMatchObject({ userKey: 'caretaker', name: 'Casey' })
})

it('reuses cached authentication configuration after a reload without a network', async () => {
  const fetchMock = vi.fn()
    .mockResolvedValueOnce(Response.json({ mode: 'dev' }))
    .mockResolvedValueOnce(Response.json({ userKey: 'caretaker', name: 'Casey' }))
  vi.stubGlobal('fetch', fetchMock)

  const onlineClient = await import('./client')
  await onlineClient.initializeAuth()
  setOfflineModeEnabled(true)
  await onlineClient.api('/api/me')

  vi.resetModules()
  fetchMock.mockRejectedValueOnce(new TypeError('Network unavailable'))
  const offlineClient = await import('./client')
  await offlineClient.initializeAuth()
  fetchMock.mockRejectedValueOnce(new TypeError('Network unavailable'))

  const cached = await offlineClient.api('/api/me')
  expect(await cached.json()).toMatchObject({ userKey: 'caretaker', name: 'Casey' })
})
