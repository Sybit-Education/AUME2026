import { useTranslation } from 'react-i18next'
import { useRouteError, useRevalidator } from 'react-router'
import { errorKey } from '@/lib/errors'

export default function RouteErrorPage() {
  const { t } = useTranslation()
  const cause = useRouteError()
  const revalidator = useRevalidator()
  return (
    <section className="card">
      <h2>{t('pages.error')}</h2>
      <p className="err" role="alert">{t(errorKey(cause, 'errors.network'))}</p>
      <button className="btn" disabled={revalidator.state !== 'idle'} onClick={() => window.location.reload()}>{t('auth.retry')}</button>
    </section>
  )
}
