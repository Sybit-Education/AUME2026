import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Navigate, useSearchParams } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { safeReturnTo } from '../auth/returnTo'
import { errorKey, type ErrorKey } from '../errors'

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
    <section aria-labelledby="login-heading">
      <h2 id="login-heading">{t('auth.login')}</h2>
      {auth.mode === 'dev' ? (
        <>
          <p>{t('auth.mockLogin')}</p>
          <div className="choices">
            {mockUsers.map((key) => (
              <button key={key} disabled={auth.busy} onClick={() => void signIn(key)}>{t(`roles.${key}`)}</button>
            ))}
          </div>
        </>
      ) : <button disabled={auth.busy} onClick={() => void signIn()}>{t('auth.login')}</button>}
      {error && <p role="alert">{t(error)}</p>}
    </section>
  )
}
