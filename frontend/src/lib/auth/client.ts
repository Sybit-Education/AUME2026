import i18n from '@/lib/i18n'
import { UiError } from '@/lib/errors'

export interface LoggedInUser {
  userKey: string
  name: string
  roles: string[]
  privileges: string[]
}

export type AuthConfig = { mode: 'dev' } | { mode: 'keycloak'; url: string; realm: string; clientId: string }
type KeycloakConfig = Extract<AuthConfig, { mode: 'keycloak' }>
type Tokens = { accessToken: string; refreshToken: string; expiresAt: number }

let configPromise: Promise<AuthConfig> | undefined
let tokens: Tokens | undefined

export function initializeAuth(): Promise<AuthConfig> {
  configPromise ??= (async () => {
    const response = await fetch('/api/auth/config')
    if (!response.ok) throw new UiError('errors.authLoad')
    const config = (await response.json()) as AuthConfig
    if (config.mode !== 'keycloak' && config.mode !== 'dev') throw new UiError('errors.authConfig')
    return config
  })()
  return configPromise
}

function tokenUrl(config: KeycloakConfig): string {
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

async function bearer(config: KeycloakConfig): Promise<string | undefined> {
  if (!tokens) return undefined
  if (tokens.expiresAt - Date.now() > 30_000) return tokens.accessToken
  const response = await fetch(tokenUrl(config), {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'refresh_token', client_id: config.clientId, refresh_token: tokens.refreshToken }),
  })
  await storeTokens(response, tokens.refreshToken, 'errors.sessionExpired')
  return tokens?.accessToken
}

export async function api(path: string, options: RequestInit = {}): Promise<Response> {
  const config = await initializeAuth()
  const headers = new Headers(options.headers)
  headers.set('Accept-Language', i18n.resolvedLanguage ?? 'de')
  if (config.mode === 'keycloak') {
    const token = await bearer(config)
    if (token) headers.set('Authorization', `Bearer ${token}`)
  }
  if (config.mode === 'dev' && options.method && !['GET', 'HEAD', 'OPTIONS'].includes(options.method)) {
    const response = await fetch('/api/auth/csrf', { credentials: 'same-origin' })
    if (!response.ok) throw new UiError('errors.sessionConfirm')
    const csrf = (await response.json()) as { headerName: string; token: string }
    headers.set(csrf.headerName, csrf.token)
  }
  return fetch(path, { ...options, headers, credentials: config.mode === 'dev' ? 'same-origin' : 'omit' })
}

export async function login(username: string, password: string): Promise<void> {
  const config = await initializeAuth()
  if (config.mode !== 'keycloak') throw new UiError('errors.login')
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

export async function devLogin(userKey: string): Promise<void> {
  const response = await api('/api/auth/dev-login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ userKey }),
  })
  if (!response.ok) throw new UiError('errors.login')
}

export async function logout(): Promise<void> {
  const config = await initializeAuth()
  if (config.mode === 'dev') {
    const response = await api('/api/auth/logout', { method: 'POST' })
    if (!response.ok) throw new UiError('errors.logout')
    return
  }
  const current = tokens
  tokens = undefined
  if (!current) return
  const response = await fetch(`${config.url}/realms/${config.realm}/protocol/openid-connect/logout`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ client_id: config.clientId, refresh_token: current.refreshToken }),
  })
  if (!response.ok) throw new UiError('errors.logout')
}
