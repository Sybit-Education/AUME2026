import { useState } from 'react'
import './App.css'

const USERS = [
  { key: 'student', label: 'Studierende' },
  { key: 'caretaker', label: 'Objektbetreuung' },
  { key: 'clerk', label: 'Sachbearbeitung' },
] as const

type Role = 'STUDENT' | 'CARETAKER' | 'CLERK'

interface LoggedInUser {
  userKey: string
  name: string
  role: Role
}

interface Residence {
  name: string
}

function App() {
  const [user, setUser] = useState<LoggedInUser | null>(null)
  const [greeting, setGreeting] = useState<string | null>(null)
  const [residences, setResidences] = useState<string[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  async function login(userKey: string) {
    setError(null)
    setGreeting(null)
    setResidences(null)
    try {
      const found = await fetch(`/api/users/search/findByUserKey?userKey=${userKey}`)
      if (!found.ok) {
        setError('Testnutzer wurde nicht gefunden.')
        return
      }
      const body = (await found.json()) as LoggedInUser
      setUser(body)
      if (body.role === 'STUDENT') {
        const response = await fetch('/api/greeting', { headers: { 'X-Dev-User': userKey } })
        if (!response.ok) {
          setError('Der Gruß konnte nicht geladen werden.')
          return
        }
        setGreeting(await response.text())
        return
      }
      const response = await fetch('/api/residences?size=100')
      if (!response.ok) {
        setError('Die Wohnanlagen konnten nicht geladen werden.')
        return
      }
      const page = (await response.json()) as { _embedded?: { residences?: Residence[] } }
      setResidences((page._embedded?.residences ?? []).map((residence) => residence.name))
    } catch {
      setError('Das Backend ist nicht erreichbar.')
    }
  }

  return (
    <main className="sample">
      <h1>Seezeit</h1>
      {user === null ? (
        <>
          <p>Testnutzer anmelden</p>
          <div className="choices">
            {USERS.map((entry) => (
              <button key={entry.key} type="button" onClick={() => login(entry.key)}>
                {entry.label}
              </button>
            ))}
          </div>
        </>
      ) : (
        <>
          <p>
            {user.name}{' '}
            <button
              type="button"
              onClick={() => {
                setUser(null)
                setGreeting(null)
                setResidences(null)
                setError(null)
              }}
            >
              Abmelden
            </button>
          </p>
          {greeting && <p>{greeting}</p>}
          {residences && (
            <ul>
              {residences.map((name) => (
                <li key={name}>{name}</li>
              ))}
            </ul>
          )}
        </>
      )}
      {error && <p role="alert">{error}</p>}
    </main>
  )
}

export default App
