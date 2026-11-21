import { useTranslation } from 'react-i18next'
import { useState } from 'react'
import { useLoaderData } from 'react-router'
import { useAuth } from '@/lib/auth/AuthContext'
import LoadingPage from '@/components/LoadingPage'
import type { loadProfile } from './api'

export default function ProfilePage() {
  const { t } = useTranslation()
  const { user } = useAuth()
  const data = useLoaderData<Awaited<ReturnType<typeof loadProfile>> | null>()
  const [representing, setRepresenting] = useState(false)
  const [offline, setOffline] = useState(false)
  if (!data) return <LoadingPage />

  const isCaretaker = user?.roles.includes('CARETAKER') ?? false

  if (!isCaretaker) {
    return (
      <div className="profile-page">
       
      </div>
    )
  }

  return (
    <div className="profile-page">
      <div className="pt"><h1>{t('navigation.profile')}</h1></div>

      <section className="card profile-caretaker">
        <div className="ch"><h2>{t('profile.signedInAs')}</h2></div>
        <div className="profile-account-select">{user?.name}</div>
      </section>

      <section className="card profile-caretaker">
        <div className="ch"><h2>{t('profile.responsibility')}</h2></div>
        <dl className="profile-details profile-details-simple">
          <div><dt>{t('profile.name')}</dt><dd>{user?.name}</dd></div>
          <div><dt>{t('profile.residences')}</dt><dd>{data.residences.join(', ') || '–'}</dd></div>
          <div><dt>{t('profile.representative')}</dt><dd>{representing ? t('profile.active') : '–'}</dd></div>
        </dl>
      </section>

      <section className="card profile-caretaker">
        <div className="ch"><h2>{t('profile.takeOverRepresentation')}</h2></div>
        <label className="chk">
          <input type="checkbox" checked={representing} onChange={(event) => setRepresenting(event.target.checked)} />
          <span>{t('profile.representColleague')}</span>
        </label>
        <p className="xs mut">{t('profile.representDescription')}</p>
      </section>

      <section className="card profile-caretaker">
        <div className="ch"><h2>{t('profile.device')}</h2></div>
        <label className="chk">
          <input type="checkbox" checked={offline} onChange={(event) => setOffline(event.target.checked)} />
          <span>{t('profile.offlineMode')}</span>
        </label>
        <p className="xs mut">{t('profile.offlineDescription')}</p>
      </section>
    </div>
  )
}
