import Keycloak from 'keycloak-js'

export interface LoggedInUser {
  userKey: string
  name: string
  roles: string[]
  privileges: string[]
}

type AuthConfig = { mode: 'dev' } | { mode: 'keycloak'; url: string; realm: string; clientId: string }
let keycloak: Keycloak | undefined
let initialization: Promise<AuthConfig> | undefined

export function initializeAuth(): Promise<AuthConfig> {
  initialization ??= (async () => {
    const response = await fetch('/api/auth/config')
    if (!response.ok) throw new Error('Die Anmeldung konnte nicht geladen werden.')
    const config = (await response.json()) as AuthConfig
    if (config.mode === 'keycloak') {
      keycloak = new Keycloak(config)
      await keycloak.init({ pkceMethod: 'S256', checkLoginIframe: false })
    } else if (config.mode !== 'dev') {
      throw new Error('Die Anmeldung ist nicht konfiguriert.')
    }
    return config
  })()
  return initialization
}

export async function api(path: string, options: RequestInit = {}): Promise<Response> {
  const config = await initializeAuth()
  const headers = new Headers(options.headers)
  if (config.mode === 'keycloak' && keycloak?.authenticated) {
    await keycloak.updateToken(30)
    headers.set('Authorization', `Bearer ${keycloak.token}`)
  }
  if (config.mode === 'dev' && options.method && !['GET', 'HEAD', 'OPTIONS'].includes(options.method)) {
    // Fetch a fresh token after login/logout; both rotate the session's CSRF token.
    const response = await fetch('/api/auth/csrf', { credentials: 'same-origin' })
    if (!response.ok) throw new Error('Die Sitzung konnte nicht bestätigt werden.')
    const csrf = (await response.json()) as { headerName: string; token: string }
    headers.set(csrf.headerName, csrf.token)
  }
  return fetch(path, { ...options, headers, credentials: config.mode === 'dev' ? 'same-origin' : 'omit' })
}

export async function login(userKey?: string): Promise<void> {
  const config = await initializeAuth()
  if (config.mode === 'keycloak') {
    await keycloak!.login({ redirectUri: window.location.origin })
    return
  }
  const response = await api('/api/auth/dev-login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ userKey }),
  })
  if (!response.ok) throw new Error('Die Anmeldung ist fehlgeschlagen.')
}

export async function logout(): Promise<void> {
  const config = await initializeAuth()
  if (config.mode === 'keycloak') {
    await keycloak!.logout({ redirectUri: window.location.origin })
    return
  }
  const response = await api('/api/auth/logout', { method: 'POST' })
  if (!response.ok) throw new Error('Die Abmeldung ist fehlgeschlagen.')
}
