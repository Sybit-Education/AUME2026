import { useTranslation } from 'react-i18next'
import { useLoaderData } from 'react-router'
import { useAuth } from '@/lib/auth/AuthContext'
import LegalFooter from '@/components/LegalFooter'
import LoadingPage from '@/components/LoadingPage'
import type { loadProfile } from './api'

export default function ProfilePage() {
  const { t } = useTranslation()
  const { user } = useAuth()
  const data = useLoaderData<Awaited<ReturnType<typeof loadProfile>> | null>()
  if (!data) return <LoadingPage />

  const isCaretaker = user?.roles.includes('CARETAKER') ?? false
  const assignedFlats = data.responsibilities.reduce((count, residence) => count + residence.flats.length, 0)
  const initial = user?.name.trim().charAt(0).toUpperCase() ?? ''

  if (!isCaretaker) {
    return (
      <div className="page-stack profile-page">
        <LegalFooter />
      </div>
    )
  }

  return (
    <div className="page-stack profile-page">
      <div className="pt"><h1>{t('navigation.profile')}</h1></div>

      <section className="card profile-caretaker profile-overview">
        <div className="profile-overview-identity">
          <span className="profile-avatar" aria-hidden="true">{initial}</span>
          <div>
            <p className="profile-overview-label">{t('profile.signedInAs')}</p>
            <h2>{user?.name}</h2>
          </div>
        </div>
        <div className="profile-stats" role="group" aria-label={t('profile.summary')}>
          <div className="profile-stat">
            <span>{t('profile.residences')}</span>
            <strong>{data.responsibilities.length}</strong>
          </div>
          <div className="profile-stat">
            <span>{t('residences.flats')}</span>
            <strong>{assignedFlats}</strong>
          </div>
        </div>
      </section>

      <section className="card profile-caretaker">
        <div className="ch"><h2>{t('profile.responsibility')}</h2></div>
        {data.responsibilities.length > 0 ? (
          <div className="profile-responsibilities">
            {data.responsibilities.map((residence) => (
              <article className="profile-responsibility" key={residence.id}>
                <div className="profile-responsibility-heading">
                  <h3>{residence.name}</h3>
                  <span className="chip t profile-role-chip">
                    {residence.kind === 'CARETAKER' && (
                      <svg className="profile-role-icon" viewBox="0 0 24 24" aria-hidden="true">
                        <path d="M4 21h16M6 21V4l8-2v19M14 8l4-1v14M9 7h1m-1 4h1m-1 4h1m4-5h1m-1 4h1M9 21v-4h3v4" />
                      </svg>
                    )}
                    {t(residence.kind === 'CARETAKER' ? 'residences.caretaker' : 'residences.deputy')}
                  </span>
                </div>
                <dl className="profile-details profile-details-simple">
                  <div>
                    <dt>{t('residences.flats')}</dt>
                    <dd>{residence.flats.length > 0 ? residence.flats.join(', ') : '–'}</dd>
                  </div>
                </dl>
              </article>
            ))}
          </div>
        ) : <p className="mut">{t('profile.noAssignments')}</p>}
      </section>
      <LegalFooter />
    </div>
  )
}
