import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { useAuth } from '@/lib/auth/AuthContext'

/** Hinweis auf die Folgen nicht behobener Mängel für die Startseite der Studierenden (#99). */
export default function ConsequenceNotice() {
  const { t } = useTranslation()
  const { user } = useAuth()
  if (!user?.roles.includes('STUDENT')) return null

  return (
    <aside className="consequence" aria-labelledby="home-consequence-title">
      <b id="home-consequence-title">{t('home.consequenceTitle')}</b>
      <p className="sm2">{t('home.consequenceText')}</p>
      {user.privileges.includes('INFO_PAGE_READ') && (
        <Link className="see-link" to="/student/inspection/appointment#inspection-process">
          {t('home.consequenceLink')}
        </Link>
      )}
    </aside>
  )
}
