import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Navigate, useSearchParams } from 'react-router'
import { useAuth } from '@/lib/auth/AuthContext'
import { safeReturnTo } from '@/lib/auth/returnTo'
import { errorKey, type ErrorKey } from '@/lib/errors'

const mockUsers = ['student', 'caretaker', 'clerk'] as const

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
    <section className="card" aria-labelledby="login-heading">
      <h2 id="login-heading">{t('auth.login')}</h2>
      {auth.mode === 'dev' ? (
        <>
          <p className="mut">{t('auth.mockLogin')}</p>
          <div className="acts">
            {mockUsers.map((key) => (
              <button className="btn" key={key} disabled={auth.busy} onClick={() => void signIn(key)}>{t(`roles.${key}`)}</button>
            ))}
          </div>
        </>
      ) : <button className="btn" disabled={auth.busy} onClick={() => void signIn()}>{t('auth.login')}</button>}
      {error && <p className="err" role="alert">{t(error)}</p>}
    </section>
  )
}
