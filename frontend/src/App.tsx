import { useEffect, useState } from 'react'
import { api, initializeAuth, login, logout, type LoggedInUser } from './auth'
import './App.css'

const USERS = [
  { key: 'student', label: 'Studierende' },
  { key: 'caretaker', label: 'Objektbetreuung' },
  { key: 'clerk', label: 'Sachbearbeitung' },
] as const

interface Dashboard {
  user: LoggedInUser
  greeting: string | null
  residences: { id: number; name: string }[] | null
}

async function loadDashboard(): Promise<Dashboard | null> {
  const response = await api('/api/me')
  if (response.status === 401) return null
  if (!response.ok) throw new Error('Das Nutzerprofil konnte nicht geladen werden.')
  const user = (await response.json()) as LoggedInUser
  let greeting: string | null = null
  let residences: Dashboard['residences'] = null
  if (user.privileges.includes('GREETING_READ')) {
    const result = await api('/api/greeting')
    if (!result.ok) throw new Error('Der Gruß konnte nicht geladen werden.')
    greeting = await result.text()
  }
  if (user.privileges.includes('RESIDENCE_READ')) {
    const result = await api('/api/residences?size=100')
    if (!result.ok) throw new Error('Die Wohnanlagen konnten nicht geladen werden.')
    residences = ((await result.json()) as { content: NonNullable<Dashboard['residences']> }).content
  }
  return { user, greeting, residences }
}

function App() {
  const [dashboard, setDashboard] = useState<Dashboard | null>(null)
  const [mode, setMode] = useState<'dev' | 'keycloak' | null>(null)
  const [busy, setBusy] = useState(true)
  const [error, setError] = useState<string | null>(null)

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
      } catch {
        if (!cancelled) setError('Die Anmeldung konnte nicht geladen werden. Bitte erneut versuchen.')
      } finally {
        if (!cancelled) setBusy(false)
      }
    }
    void restoreSession()
    return () => { cancelled = true }
  }, [])

  async function signIn(userKey?: string) {
    setBusy(true)
    setError(null)
    setDashboard(null)
    try {
      await login(userKey)
      const loaded = await loadDashboard()
      if (!loaded) throw new Error('Die Sitzung ist abgelaufen. Bitte erneut anmelden.')
      setDashboard(loaded)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Das Backend ist nicht erreichbar.')
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
      setError('Die Abmeldung ist fehlgeschlagen. Bitte erneut versuchen.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="sample" aria-busy={busy}>
      <h1>Seezeit</h1>
      {dashboard === null ? (
        mode === 'dev' ? (
          <>
            <p>Testnutzer anmelden</p>
            <div className="choices">
              {USERS.map((entry) => (
                <button key={entry.key} type="button" disabled={busy} onClick={() => void signIn(entry.key)}>
                  {entry.label}
                </button>
              ))}
            </div>
          </>
        ) : mode === 'keycloak' ? (
          <button type="button" disabled={busy} onClick={() => void signIn()}>Anmelden</button>
        ) : !busy && (
          <button type="button" onClick={() => window.location.reload()}>Erneut versuchen</button>
        )
      ) : (
        <>
          <p>
            {dashboard.user.name}{' '}
            <button type="button" disabled={busy} onClick={() => void signOut()}>Abmelden</button>
          </p>
          {dashboard.greeting && <p>{dashboard.greeting}</p>}
          {dashboard.residences && (
            <ul>
              {dashboard.residences.map((residence) => <li key={residence.id}>{residence.name}</li>)}
            </ul>
          )}
        </>
      )}
      {busy && <p role="status">Wird geladen …</p>}
      {error && <p role="alert">{error}</p>}
    </main>
  )
}

export default App
