import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Navigate, useSearchParams } from 'react-router'
import LegalFooter from '@/components/LegalFooter'
import { useAuth } from '@/lib/auth/AuthContext'
import { safeReturnTo } from '@/lib/auth/returnTo'
import { errorKey, type ErrorKey } from '@/lib/errors'
import './login.css'

export default function LoginPage() {
  const { t } = useTranslation()
  const auth = useAuth()
  const [params] = useSearchParams()
  const returnTo = safeReturnTo(params.get('returnTo'))
  const [error, setError] = useState<ErrorKey | null>(null)
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

  return (
    <div className="page-stack">
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
      <LegalFooter />
    </div>
  )
}
