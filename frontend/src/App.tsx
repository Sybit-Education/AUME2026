import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { supportedLanguage } from './i18n'
import { UiError, errorKey, type ErrorKey } from './errors'
import { api, initializeAuth, login, logout, type LoggedInUser } from './auth'
import './App.css'

const USERS = [
  { key: 'student' },
  { key: 'caretaker' },
  { key: 'clerk' },
] as const

interface Dashboard {
  user: LoggedInUser
  greeting: string | null
  residences: { id: number; name: string }[] | null
}

async function loadDashboard(): Promise<Dashboard | null> {
  const response = await api('/api/me')
  if (response.status === 401) return null
  if (!response.ok) throw new UiError('errors.profile')
  const user = (await response.json()) as LoggedInUser
  let greeting: string | null = null
  let residences: Dashboard['residences'] = null
  if (user.privileges.includes('GREETING_READ')) {
    const result = await api('/api/greeting')
    if (!result.ok) throw new UiError('errors.greeting')
    greeting = await result.text()
  }
  if (user.privileges.includes('RESIDENCE_READ')) {
    const result = await api('/api/residences?size=100')
    if (!result.ok) throw new UiError('errors.residences')
    residences = ((await result.json()) as { content: NonNullable<Dashboard['residences']> }).content
  }
  return { user, greeting, residences }
}

function App() {
  const { t, i18n } = useTranslation()
  const language = i18n.resolvedLanguage ?? 'de'
  const [dashboard, setDashboard] = useState<Dashboard | null>(null)
  const [mode, setMode] = useState<'dev' | 'keycloak' | null>(null)
  const [busy, setBusy] = useState(true)
  const [error, setError] = useState<ErrorKey | null>(null)

  useEffect(() => {
    let cancelled = false
    async function restoreSession() {
      try {
        const config = await initializeAuth()
        const restored = await loadDashboard()
        if (!cancelled) {
          setMode(config.mode)
          setDashboard(restored)
        }
      } catch (cause) {
        if (!cancelled) setError(errorKey(cause, 'errors.authLoad'))
      } finally {
        if (!cancelled) setBusy(false)
      }
    }
    void restoreSession()
    return () => { cancelled = true }
  }, [language])

  async function signIn(userKey?: string) {
    setBusy(true)
    setError(null)
    setDashboard(null)
    try {
      await login(userKey)
      const loaded = await loadDashboard()
      if (!loaded) throw new UiError('errors.sessionExpired')
      setDashboard(loaded)
    } catch (cause) {
      setError(errorKey(cause, 'errors.network'))
    } finally {
      setBusy(false)
    }
  }

  async function signOut() {
    setBusy(true)
    setError(null)
    try {
      await logout()
      setDashboard(null)
    } catch {
      setError('errors.logout')
    } finally {
      setBusy(false)
    }
  }

  async function changeLanguage(value: string) {
    const next = supportedLanguage(value)
    if (!next || next === language) return
    setBusy(true)
    try {
      // The locale effect reloads server-rendered content and finishes the busy state.
      await i18n.changeLanguage(next)
    } catch {
      setError('errors.language')
      setBusy(false)
    }
  }

  return (
    <main className="sample" aria-busy={busy}>
      <header className="sample-header">
        <h1>{t('app.name')}</h1>
        <label className="language-picker">
          {t('app.language')}
          <select value={language} disabled={busy} onChange={(event) => void changeLanguage(event.target.value)}>
            <option value="de" lang="de">Deutsch</option>
            <option value="en" lang="en">English</option>
          </select>
        </label>
      </header>
      {dashboard === null ? (
        mode === 'dev' ? (
          <>
            <p>{t('auth.mockLogin')}</p>
            <div className="choices">
              {USERS.map((entry) => (
                <button key={entry.key} type="button" disabled={busy} onClick={() => void signIn(entry.key)}>
                  {t(`roles.${entry.key}`)}
                </button>
              ))}
            </div>
          </>
        ) : mode === 'keycloak' ? (
          <button type="button" disabled={busy} onClick={() => void signIn()}>{t('auth.login')}</button>
        ) : !busy && (
          <button type="button" onClick={() => window.location.reload()}>{t('auth.retry')}</button>
        )
      ) : (
        <>
          <p>
            {dashboard.user.name}{' '}
            <button type="button" disabled={busy} onClick={() => void signOut()}>{t('auth.logout')}</button>
          </p>
          {!busy && dashboard.greeting && <p>{dashboard.greeting}</p>}
          {dashboard.residences && (
            <ul>
              {dashboard.residences.map((residence) => <li key={residence.id}>{residence.name}</li>)}
            </ul>
          )}
        </>
      )}
      {busy && <p role="status">{t('app.loading')}</p>}
      {error && <p role="alert">{t(error)}</p>}
    </main>
  )
}

export default App
