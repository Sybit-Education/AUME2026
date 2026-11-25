import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { useAuth } from '@/lib/auth/AuthContext'
import { moreGroups } from './menu'

/** „Mehr“ der Studierenden: weitere Funktionen thematisch gruppiert (Wireframe „Mehr“). */
export default function MorePage() {
  const { t } = useTranslation()
  const { user } = useAuth()
  const allowed = (privilege?: string) => !privilege || (user?.privileges.includes(privilege) ?? false)
  const groups = moreGroups
    .map((group) => ({ ...group, items: group.items.filter((item) => allowed(item.privilege)) }))
    .filter((group) => group.items.length > 0)

  return (
    <>
      <div className="ch"><h2>{t('more.title')}</h2></div>
      {groups.map((group) => (
        <section key={group.title} aria-labelledby={`more-${group.title}`} className="more-group">
          <h3 id={`more-${group.title}`}>{t(group.title)}</h3>
          <div className="card more-list">
            {group.items.map((item) => (
              <Link key={item.to} to={item.to} className="row more-link">
                <span className="more-icon" aria-hidden="true">{item.icon}</span>
                <span className="more-text">
                  <b>{t(item.label)}</b>
                  <span className="mut xs">{t(item.hint)}</span>
                </span>
                <span className="more-chevron" aria-hidden="true">›</span>
              </Link>
            ))}
          </div>
        </section>
      ))}
    </>
  )
}
