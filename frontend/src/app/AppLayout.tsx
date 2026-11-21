import { useEffect, useRef, useState, type PropsWithChildren } from 'react'
import { useTranslation } from 'react-i18next'
import { NavLink, Outlet, useNavigation, useRevalidator } from 'react-router'
import { useAuth } from '@/lib/auth/AuthContext'
import { errorKey, type ErrorKey } from '@/lib/errors'
import { supportedLanguage } from '@/lib/i18n'
import { isOfflineModeEnabled, offlineModeChangeEvent } from '@/lib/offline'
import { navigation } from './navigation'

export default function AppLayout({ children }: PropsWithChildren) {
  const { t, i18n } = useTranslation()
  const language = i18n.resolvedLanguage ?? 'de'
  const previousLanguage = useRef(language)
  const auth = useAuth()
  const transition = useNavigation()
  const revalidator = useRevalidator()
  const busy = auth.busy || transition.state !== 'idle' || revalidator.state !== 'idle'
  const [error, setError] = useState<ErrorKey | null>(null)
  const [offlineMode, setOfflineMode] = useState(isOfflineModeEnabled)

  useEffect(() => {
    if (previousLanguage.current !== language) {
      previousLanguage.current = language
      void revalidator.revalidate()
    }
  }, [language, revalidator])

  useEffect(() => {
    const updateOfflineMode = () => setOfflineMode(isOfflineModeEnabled())
    window.addEventListener(offlineModeChangeEvent, updateOfflineMode)
    return () => window.removeEventListener(offlineModeChangeEvent, updateOfflineMode)
  }, [])

  async function changeLanguage(value: string) {
    const next = supportedLanguage(value)
    if (!next || next === language) return
    try { await i18n.changeLanguage(next) } catch { setError('errors.language') }
  }

  async function signOut() {
    setError(null)
    try { await auth.signOut() } catch (cause) { setError(errorKey(cause, 'errors.logout')) }
  }

  return (
    <div aria-busy={busy}>
      <header className="hd">
        <h1 className="brand">{t('app.name')}</h1>
        {auth.user && (
          <nav className="seg" aria-label={t('navigation.main')}>
            {navigation.filter((item) => !item.privilege || auth.user!.privileges.includes(item.privilege)).map((item) => (
              <NavLink key={item.to} to={item.to} end={item.to === '/'} className={({ isActive }) => isActive ? 'on' : undefined}>{t(item.label)}</NavLink>
            ))}
          </nav>
        )}
        <label className="language-picker">
          {t('app.language')}
          <select value={language} disabled={busy} onChange={(event) => void changeLanguage(event.target.value)}>
            <option value="de" lang="de">Deutsch</option>
            <option value="en" lang="en">English</option>
          </select>
        </label>
        {auth.user && (
          <>
            <span className="who">{auth.user.name}</span>
            <button className="btn gh" disabled={busy} onClick={() => void signOut()}>{t('auth.logout')}</button>
          </>
        )}
      </header>
      <div className="mn">
        {busy && <p role="status"><span className="loader" aria-hidden="true" /> {t('app.loading')}</p>}
        {error && <p className="err" role="alert">{t(error)}</p>}
        {auth.user && offlineMode && (
          <p className="note offline-mode-notice" role="status">{t('app.offlineModeNotice')}</p>
        )}
        <main>{children ?? <Outlet />}</main>
      </div>
    </div>
  )
}
