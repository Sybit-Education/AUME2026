import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Navigate, useSearchParams } from 'react-router'
import LegalFooter from '@/components/LegalFooter'
import { useAuth } from '@/lib/auth/AuthContext'
import { safeReturnTo } from '@/lib/auth/returnTo'
import { errorKey, type ErrorKey } from '@/lib/errors'
import './login.css'

// caretaker2 betreut Petershausen und vertritt Sonne; zum Testen der Zuständigkeit (US-03).
const mockUsers = ['student', 'caretaker', 'caretaker2', 'clerk'] as const

export default function LoginPage() {
  const { t } = useTranslation()
  const auth = useAuth()
  const [params] = useSearchParams()
  const returnTo = safeReturnTo(params.get('returnTo'))
  const [error, setError] = useState<ErrorKey | null>(null)
  if (auth.user) return <Navigate replace to={returnTo} />

  async function signIn(key?: string) {
    setError(null)
    try { await auth.signIn(key, returnTo) } catch (cause) { setError(errorKey(cause, 'errors.network')) }
  }

  return (
    <div className="page-stack">
      <section className="card login-card" aria-labelledby="login-heading">
        <h2 id="login-heading">{t('auth.login')}</h2>
        {auth.mode === 'dev' && (
          <>
            <p className="mut">{t('auth.mockLogin')}</p>
            <div className="acts">
              {mockUsers.map((key) => (
                <button className="btn" type="button" key={key} disabled={auth.busy} onClick={() => void signIn(key)}>{t(`roles.${key}`)}</button>
              ))}
            </div>
            <hr className="login-split" />
          </>
        )}
        <form className="login-account" onSubmit={(event) => event.preventDefault()}>
          <p className="mut">{t('auth.accountHint')}</p>
          <div className="fld">
            <label htmlFor="login-email">{t('auth.email')}</label>
            <input id="login-email" name="email" type="email" autoComplete="email" />
          </div>
          <div className="fld">
            <label htmlFor="login-password">{t('auth.password')}</label>
            <input id="login-password" name="password" type="password" autoComplete="current-password" />
          </div>
          <button className="btn lg blk" type="submit" disabled={auth.busy}>{t('auth.accountLogin')}</button>
        </form>
        {error && <p className="err" role="alert">{t(error)}</p>}
      </section>
      <LegalFooter />
    </div>
  )
}
