import { UiError } from './errors'

const enabledKey = 'aume.offline.enabled'
const authConfigKey = 'aume.offline.auth-config'
const ownerKey = 'aume.offline.owner'
const responsePrefix = 'aume.offline.response.'

interface CachedResponse {
  body: string
  contentType: string
}

function storage(): Storage {
  try {
    return window.localStorage
  } catch {
    throw new UiError('errors.offlineStorage')
  }
}

function write(store: Storage, key: string, value: string) {
  try {
    store.setItem(key, value)
  } catch {
    throw new UiError('errors.offlineStorage')
  }
}

function remove(store: Storage, key: string) {
  try {
    store.removeItem(key)
  } catch {
    throw new UiError('errors.offlineStorage')
  }
}

function isCacheable(path: string) {
  return path === '/api/me'
    || path === '/api/greeting'
    || path.startsWith('/api/residences?')
}

function clearResponses(store: Storage) {
  const keys: string[] = []
  for (let index = 0; index < store.length; index++) {
    const key = store.key(index)
    if (key?.startsWith(responsePrefix)) keys.push(key)
  }
  keys.forEach((key) => remove(store, key))
}

function responseKey(userKey: string, path: string) {
  return `${responsePrefix}${encodeURIComponent(userKey)}:${encodeURIComponent(path)}`
}

export function isOfflineModeEnabled() {
  try {
    return storage().getItem(enabledKey) === 'true'
  } catch (cause) {
    console.error('Unable to read offline mode preference.', cause)
    return false
  }
}

export function setOfflineModeEnabled(enabled: boolean) {
  const store = storage()
  if (enabled) {
    write(store, enabledKey, 'true')
    return
  }

  remove(store, enabledKey)
  remove(store, ownerKey)
  clearResponses(store)
}

export function clearOfflineData() {
  const store = storage()
  remove(store, enabledKey)
  remove(store, authConfigKey)
  remove(store, ownerKey)
  clearResponses(store)
}

export function saveAuthConfig(config: unknown) {
  try {
    write(storage(), authConfigKey, JSON.stringify(config))
  } catch (cause) {
    console.error('Unable to cache authentication configuration.', cause)
  }
}

export function getCachedAuthConfig<T>() {
  if (!isOfflineModeEnabled()) return null
  const raw = storage().getItem(authConfigKey)
  return raw ? JSON.parse(raw) as T : null
}

export async function cacheApiResponse(path: string, response: Response) {
  if (!isOfflineModeEnabled() || !response.ok || !isCacheable(path)) return

  const store = storage()
  const body = await response.clone().text()
  let userKey = store.getItem(ownerKey)

  if (path === '/api/me') {
    const user = JSON.parse(body) as { userKey?: string }
    if (!user.userKey) return
    if (userKey && userKey !== user.userKey) clearResponses(store)
    userKey = user.userKey
    write(store, ownerKey, userKey)
  }

  if (!userKey) return
  const cached: CachedResponse = {
    body,
    contentType: response.headers.get('Content-Type') ?? 'application/json',
  }
  write(store, responseKey(userKey, path), JSON.stringify(cached))
}

export function getCachedApiResponse(path: string) {
  if (!isOfflineModeEnabled() || !isCacheable(path)) return null

  const store = storage()
  const userKey = store.getItem(ownerKey)
  if (!userKey) return null
  const raw = store.getItem(responseKey(userKey, path))
  if (!raw) return null

  const cached = JSON.parse(raw) as CachedResponse
  return new Response(cached.body, {
    status: 200,
    headers: { 'Content-Type': cached.contentType },
  })
}
