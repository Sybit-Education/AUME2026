import i18n from '@/lib/i18n'
import { UiError } from '@/lib/errors'

export interface LoggedInUser {
  userKey: string
  name: string
  roles: string[]
  privileges: string[]
}

type AuthConfig = { mode: 'keycloak'; url: string; realm: string; clientId: string }
type Tokens = { accessToken: string; refreshToken: string; expiresAt: number }

let configPromise: Promise<AuthConfig> | undefined
let tokens: Tokens | undefined

export function initializeAuth(): Promise<AuthConfig> {
  configPromise ??= (async () => {
    const response = await fetch('/api/auth/config')
    if (!response.ok) throw new UiError('errors.authLoad')
    const config = (await response.json()) as AuthConfig
    if (config.mode !== 'keycloak') throw new UiError('errors.authConfig')
    return config
  })()
  return configPromise
}

function tokenUrl(config: AuthConfig): string {
  return `${config.url}/realms/${config.realm}/protocol/openid-connect/token`
}

async function storeTokens(response: Response, refreshToken: string | undefined, failure: 'errors.login' | 'errors.sessionExpired'): Promise<void> {
  if (!response.ok) {
    if (failure === 'errors.sessionExpired') tokens = undefined
    throw new UiError(failure)
  }
  const payload = (await response.json()) as { access_token: string; refresh_token?: string; expires_in: number }
  tokens = {
    accessToken: payload.access_token,
    refreshToken: payload.refresh_token ?? refreshToken ?? '',
    expiresAt: Date.now() + payload.expires_in * 1000,
  }
}

async function bearer(): Promise<string | undefined> {
  if (!tokens) return undefined
  if (tokens.expiresAt - Date.now() > 30_000) return tokens.accessToken
  const config = await initializeAuth()
  const response = await fetch(tokenUrl(config), {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'refresh_token', client_id: config.clientId, refresh_token: tokens.refreshToken }),
  })
  await storeTokens(response, tokens.refreshToken, 'errors.sessionExpired')
  return tokens?.accessToken
}

export async function api(path: string, options: RequestInit = {}): Promise<Response> {
  await initializeAuth()
  const headers = new Headers(options.headers)
  headers.set('Accept-Language', i18n.resolvedLanguage ?? 'de')
  const token = await bearer()
  if (token) headers.set('Authorization', `Bearer ${token}`)
  return fetch(path, { ...options, headers, credentials: 'omit' })
}

export async function login(username: string, password: string): Promise<void> {
  const config = await initializeAuth()
  const response = await fetch(tokenUrl(config), {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'password',
      client_id: config.clientId,
      username,
      password,
    }),
  })
  await storeTokens(response, undefined, 'errors.login')
}

export async function logout(): Promise<void> {
  const current = tokens
  tokens = undefined
  if (!current) return
  const config = await initializeAuth()
  const response = await fetch(`${config.url}/realms/${config.realm}/protocol/openid-connect/logout`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ client_id: config.clientId, refresh_token: current.refreshToken }),
  })
  if (!response.ok) throw new UiError('errors.logout')
}
