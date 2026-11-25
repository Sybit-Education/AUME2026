import { useTranslation } from 'react-i18next'
import { Link, useLoaderData } from 'react-router'
import { useAuth } from '@/lib/auth/AuthContext'
import LegalFooter from '@/components/LegalFooter'
import LoadingPage from '@/components/LoadingPage'
import type { loadDashboard } from './api'

export default function DashboardPage() {
  const { t } = useTranslation()
  const { user } = useAuth()
  const data = useLoaderData<Awaited<ReturnType<typeof loadDashboard>> | null>()

  return (
    <div className="page-stack">
      {!data ? <LoadingPage /> : (
        <section className="card">
          <h2>{t('navigation.dashboard')}</h2>
          <p>{t('pages.welcome', { name: user?.name })}</p>
          {data.greeting && <p>{data.greeting}</p>}
          {user?.privileges.includes('RESIDENCE_READ') && <p className="acts"><Link className="btn sec" to="/residences">{t('pages.openResidences')}</Link></p>}
        </section>
      )}
      <LegalFooter />
    </div>
  )
}
