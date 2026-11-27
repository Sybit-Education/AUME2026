import { useEffect, useRef, useState, type PropsWithChildren } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, matchPath, NavLink, Outlet, useLocation, useMatches, useNavigation, useRevalidator } from 'react-router'
import { useAuth } from '@/lib/auth/AuthContext'
import NavIcon from '@/components/NavIcon'
import NotificationBell from '@/features/student-messages/NotificationBell'
import { errorKey, type ErrorKey } from '@/lib/errors'
import { supportedLanguage } from '@/lib/i18n'
import type { PageHandle } from '@/lib/pageHandle'
import { profileNavigation } from '@/features/profile/routes'
import { navigation, studentTabs, usesStudentView } from './navigation'

export default function AppLayout({ children }: PropsWithChildren) {
  const { t, i18n } = useTranslation()
  const language = i18n.resolvedLanguage ?? 'de'
  const previousLanguage = useRef(language)
  const auth = useAuth()
  const { pathname } = useLocation()
  const transition = useNavigation()
  const revalidator = useRevalidator()
  const busy = auth.busy || transition.state !== 'idle' || revalidator.state !== 'idle'
  const [error, setError] = useState<ErrorKey | null>(null)
  const studentView = auth.user ? usesStudentView(auth.user.roles) : false
  const allowed = (privilege?: string) => !privilege || auth.user!.privileges.includes(privilege)
  // Titel und Zurück-Ziel der Kopfleiste kommen aus der tiefsten Route mit `handle.title`.
  const page = useMatches().map((match) => match.handle as PageHandle | undefined)
    .filter((handle) => handle?.title).at(-1)

  useEffect(() => {
    if (previousLanguage.current !== language) {
      previousLanguage.current = language
      void revalidator.revalidate()
    }
  }, [language, revalidator])

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
    <div className="app" aria-busy={busy}>
      {!studentView && <header className="hd">
        <h1 className="brand">{t('app.name')}</h1>
        {auth.user && !studentView && (
          <nav className="seg" aria-label={t('navigation.main')}>
            {navigation.filter((item) => allowed(item.privilege)).map((item) => (
              <NavLink key={item.to} to={item.to} end={item.to === '/'} className={({ isActive }) => isActive ? 'on' : undefined}>{t(item.label)}</NavLink>
            ))}
          </nav>
        )}
        {/* Studierende wählen die Sprache unter Mehr → Einstellungen (US-26). */}
        {!studentView && (
          <label className="language-picker">
            {t('app.language')}
            <select value={language} disabled={busy} onChange={(event) => void changeLanguage(event.target.value)}>
              <option value="de" lang="de">Deutsch</option>
              <option value="en" lang="en">English</option>
            </select>
          </label>
        )}
        {auth.user && (
          <>
            {/* Wer ein Profil hat, findet es rechts an Stelle des Namens; sonst bleibt der Name stehen. */}
            {allowed(profileNavigation.privilege) ? (
              <NavLink to={profileNavigation.to} className={({ isActive }) => isActive ? 'profile-link on' : 'profile-link'}
                aria-label={t(profileNavigation.label)} title={t(profileNavigation.label)}>
                <svg viewBox="0 0 24 24" width="22" height="22" aria-hidden="true">
                  <circle cx="12" cy="8" r="4" />
                  <path d="M4.5 20a7.5 7.5 0 0 1 15 0" />
                </svg>
              </NavLink>
            ) : <span className="who">{auth.user.name}</span>}
            <button className="btn gh" disabled={busy} onClick={() => void signOut()}>{t('auth.logout')}</button>
          </>
        )}
      </header>}
      {studentView ? (
        <div className="st-wrap">
          {/* App-Kopfleiste wie im Wireframe: Zurück, Seitentitel, Abmelden. */}
          <header className={pathname === '/login' ? 'st-top' : 'st-top st-top-with-notifications'}>
            {page?.back ? (
              <Link className="st-icon-btn" to={page.back} aria-label={t('app.back')}>
                <svg viewBox="0 0 24 24" width="24" height="24" aria-hidden="true"><path d="m14.5 5.5-6.5 6.5 6.5 6.5" /></svg>
              </Link>
            ) : <span className="st-icon-btn" aria-hidden="true" />}
            <h1>{page ? t(page.title, { name: auth.user?.name }) : t('app.name')}</h1>
            <div className="student-header-actions">
              {auth.user && pathname !== '/login' && <NotificationBell user={auth.user} />}
              <button className="st-icon-btn" type="button" disabled={busy} aria-label={t('auth.logout')} title={t('auth.logout')}
                onClick={() => void signOut()}>
                <svg viewBox="0 0 24 24" width="22" height="22" aria-hidden="true">
                  <path d="M10 4.5H6.5A1.5 1.5 0 0 0 5 6v12a1.5 1.5 0 0 0 1.5 1.5H10" />
                  <path d="m14 8 4 4-4 4M18 12H9" />
                </svg>
              </button>
            </div>
          </header>
          <div className="st-body">
            {busy && <p role="status"><span className="loader" aria-hidden="true" /> {t('app.loading')}</p>}
            {error && <p className="err" role="alert">{t(error)}</p>}
            <main>{children ?? <Outlet />}</main>
          </div>
          <nav className="tabbar" aria-label={t('navigation.studentTabs')}>
            {studentTabs.filter((item) => allowed(item.privilege)).map((item) => {
              const content = <><span className="i"><NavIcon name={item.icon} /></span>{t(item.label)}</>
              if (item.activeWithin) {
                const active = item.activeWithin.some((pattern) => matchPath(pattern, pathname) !== null)
                return (
                  <Link key={item.to} to={item.to} className={active ? 'on' : undefined} aria-current={active ? 'page' : undefined}>
                    {content}
                  </Link>
                )
              }
              return (
                <NavLink key={item.to} to={item.to} end={item.to === '/'} className={({ isActive }) => isActive ? 'on' : undefined}>
                  {content}
                </NavLink>
              )
            })}
          </nav>
        </div>
      ) : (
        <div className="mn">
          {busy && <p role="status"><span className="loader" aria-hidden="true" /> {t('app.loading')}</p>}
          {error && <p className="err" role="alert">{t(error)}</p>}
          <main>{children ?? <Outlet />}</main>
        </div>
      )}
    </div>
  )
}
