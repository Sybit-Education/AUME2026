import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'

export default function NotFoundPage() {
  const { t } = useTranslation()
  return <section><h2>{t('pages.notFound')}</h2><p>{t('pages.notFoundDescription')}</p><Link to="/">{t('navigation.dashboard')}</Link></section>
}
