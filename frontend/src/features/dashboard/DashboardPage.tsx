import type { PropsWithChildren, ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useLoaderData } from 'react-router'
import { useAuth } from '@/lib/auth/AuthContext'
import LoadingPage from '@/components/LoadingPage'
import type { loadDashboard } from './api'
import './dashboard.css'

/**
 * Mit `header` ersetzt ein schlichter Kopfbereich die Begrüßungskarte, wie auf der Startseite der
 * Studierenden im Wireframe (#99). Die Überschrift steht dort in der Kopfleiste der App.
 */
export default function DashboardPage({ header, children }: PropsWithChildren<{ header?: ReactNode }>) {
  const { t } = useTranslation()
  const { user } = useAuth()
  const data = useLoaderData<Awaited<ReturnType<typeof loadDashboard>> | null>()

  if (!data) return <LoadingPage />

  return (
    <>
      {header ? (
        <div className="dashboard-header">{header}</div>
      ) : (
        <section className="card">
          <h2>{t('navigation.dashboard')}</h2>
          <p>{t('pages.welcome', { name: user?.name })}</p>
          {data.greeting && <p>{data.greeting}</p>}
          {user?.privileges.includes('RESIDENCE_READ') && <p className="acts"><Link className="btn sec" to="/residences">{t('pages.openResidences')}</Link></p>}
        </section>
      )}
      {children}
    </>
  )
}
