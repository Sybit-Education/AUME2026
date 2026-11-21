// @vitest-environment jsdom
import { beforeEach, expect, it } from 'vitest'
import {
  cacheApiResponse,
  getCachedApiResponse,
  isOfflineModeEnabled,
  setOfflineModeEnabled,
} from './offline'

beforeEach(() => {
  window.localStorage.clear()
})

it('caches approved API responses for the active user while offline mode is enabled', async () => {
  setOfflineModeEnabled(true)
  await cacheApiResponse('/api/me', Response.json({ userKey: 'caretaker' }))
  await cacheApiResponse('/api/greeting', new Response('Hallo Objektbetreuung'))

  const cached = getCachedApiResponse('/api/greeting')
  expect(cached && await cached.text()).toBe('Hallo Objektbetreuung')
  expect(getCachedApiResponse('/api/unlisted')).toBeNull()
})

it('does not expose cached data after changing the cached user', async () => {
  setOfflineModeEnabled(true)
  await cacheApiResponse('/api/me', Response.json({ userKey: 'caretaker' }))
  await cacheApiResponse('/api/greeting', new Response('Caretaker greeting'))

  await cacheApiResponse('/api/me', Response.json({ userKey: 'clerk' }))

  expect(getCachedApiResponse('/api/greeting')).toBeNull()
})

it('clears cached API data when offline mode is disabled', async () => {
  setOfflineModeEnabled(true)
  await cacheApiResponse('/api/me', Response.json({ userKey: 'caretaker' }))
  await cacheApiResponse('/api/greeting', new Response('Hallo'))

  setOfflineModeEnabled(false)

  expect(isOfflineModeEnabled()).toBe(false)
  expect(getCachedApiResponse('/api/greeting')).toBeNull()
})
