import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'

export default function NotFoundPage() {
  const { t } = useTranslation()
  return <section className="card"><h2>{t('pages.notFound')}</h2><p className="mut">{t('pages.notFoundDescription')}</p><Link className="btn sec" to="/">{t('navigation.dashboard')}</Link></section>
}
