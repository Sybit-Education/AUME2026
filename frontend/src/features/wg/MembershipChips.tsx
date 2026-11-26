import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import type { Membership } from './api'

/** Eigene WG und Wohnanlage, etwa unter der Begrüßung auf der Startseite (#99). */
export default function MembershipChips({ membership, loadFailed = false }: {
  membership: Membership | null
  loadFailed?: boolean
}) {
  const { t } = useTranslation()
  if (loadFailed) return <p className="err" role="alert">{t('errors.wg')}</p>
  if (!membership) {
    return (
      <p className="mut sm2">
        {t('home.noFlat')} <Link to="/student/flat">{t('home.joinFlat')}</Link>
      </p>
    )
  }
  return (
    <p className="sw" aria-label={t('home.membership')}>
      <span className="chip t">{t('home.flat', { flat: membership.flat })}</span>
      <span className="chip">{membership.residence}</span>
    </p>
  )
}
