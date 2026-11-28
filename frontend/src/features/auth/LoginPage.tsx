import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Navigate, useSearchParams } from 'react-router'
import { useAuth } from '@/lib/auth/AuthContext'
import { safeReturnTo } from '@/lib/auth/returnTo'
import { errorKey, type ErrorKey } from '@/lib/errors'
import './login.css'

const devUsers = ['student', 'caretaker', 'caretaker2', 'clerk'] as const

export default function LoginPage() {
  const { t } = useTranslation()
  const auth = useAuth()
  const [params] = useSearchParams()
  const returnTo = safeReturnTo(params.get('returnTo'))
  const [error, setError] = useState<ErrorKey | null>(null)
  const [devUser, setDevUser] = useState<(typeof devUsers)[number]>('student')
  if (auth.user) return <Navigate replace to={returnTo} />

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    setError(null)
    try {
      await auth.signIn(String(form.get('username') ?? ''), String(form.get('password') ?? ''))
    } catch (cause) {
      setError(errorKey(cause, 'errors.network'))
    }
  }

  async function devSubmit() {
    setError(null)
    try {
      await auth.signInDev(devUser)
    } catch (cause) {
      setError(errorKey(cause, 'errors.network'))
    }
  }

  return (
    <>
      <section className="card login-card" aria-labelledby="login-heading">
        <h2 id="login-heading">{t('auth.login')}</h2>
        <form className="login-account" onSubmit={(event) => void submit(event)}>
          <p className="mut">{t('auth.accountHint')}</p>
          <div className="fld">
            <label htmlFor="login-username">{t('auth.username')}</label>
            <input id="login-username" name="username" type="text" autoComplete="username" required />
          </div>
          <div className="fld">
            <label htmlFor="login-password">{t('auth.password')}</label>
            <input id="login-password" name="password" type="password" autoComplete="current-password" required />
          </div>
          <button className="btn lg blk" type="submit" disabled={auth.busy}>{t('auth.accountLogin')}</button>
        </form>
        {error && <p className="err" role="alert">{t(error)}</p>}
      </section>
      {auth.mode === 'dev' && (
        <section className="card login-card login-dev">
          <div className="fld">
            <label htmlFor="dev-user">{t('auth.devUser')}</label>
            <select id="dev-user" value={devUser} onChange={(event) => setDevUser(event.target.value as (typeof devUsers)[number])}>
              {devUsers.map((key) => <option key={key} value={key}>{t(`roles.${key}`)}</option>)}
            </select>
          </div>
          <button className="btn lg blk" type="button" disabled={auth.busy} onClick={() => void devSubmit()}>
            <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true" focusable="false">
              <path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z" />
            </svg>
            {t('auth.devLogin')}
          </button>
          <p className="login-dev-hint">{t('auth.devLoginHint')}</p>
        </section>
      )}
    </>
  )
}
