import { useTranslation } from 'react-i18next'
import { useState } from 'react'
import { useLoaderData } from 'react-router'
import { useAuth } from '@/lib/auth/AuthContext'
import { errorKey, type ErrorKey } from '@/lib/errors'
import { isOfflineModeEnabled, setOfflineModeEnabled } from '@/lib/offline'
import LoadingPage from '@/components/LoadingPage'
import { prepareOfflineMode } from './api'
import type { loadProfile } from './api'

export default function ProfilePage() {
  const { t } = useTranslation()
  const { user } = useAuth()
  const data = useLoaderData<Awaited<ReturnType<typeof loadProfile>> | null>()
  const [representing, setRepresenting] = useState(false)
  const [offline, setOffline] = useState(isOfflineModeEnabled)
  const [offlineBusy, setOfflineBusy] = useState(false)
  const [offlineError, setOfflineError] = useState<ErrorKey | null>(null)
  if (!data) return <LoadingPage />

  async function changeOfflineMode(enabled: boolean) {
    setOfflineBusy(true)
    setOfflineError(null)
    try {
      if (enabled) {
        if (!user) return
        await prepareOfflineMode(user, new AbortController().signal)
      } else {
        setOfflineModeEnabled(false)
      }
      setOffline(enabled)
    } catch (cause) {
      setOffline(isOfflineModeEnabled())
      setOfflineError(errorKey(cause, 'errors.offlineSetup'))
    } finally {
      setOfflineBusy(false)
    }
  }

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
          <input
            type="checkbox"
            checked={offline}
            disabled={offlineBusy}
            onChange={(event) => void changeOfflineMode(event.target.checked)}
          />
          <span>{t('profile.offlineMode')}</span>
        </label>
        <p className="xs mut">{t('profile.offlineDescription')}</p>
        {offline && <p className="xs" role="status">{t('profile.offlineReady')}</p>}
        {offlineError && <p className="err" role="alert">{t(offlineError)}</p>}
      </section>
    </div>
  )
}
