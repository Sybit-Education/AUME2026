import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'

export default function ForbiddenPage() {
  const { t } = useTranslation()
  return <section className="card"><h2>{t('pages.forbidden')}</h2><p className="mut">{t('pages.forbiddenDescription')}</p><Link className="btn sec" to="/">{t('navigation.dashboard')}</Link></section>
}
