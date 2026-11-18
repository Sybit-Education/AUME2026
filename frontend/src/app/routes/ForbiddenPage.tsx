import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'

export default function ForbiddenPage() {
  const { t } = useTranslation()
  return <section><h2>{t('pages.forbidden')}</h2><p>{t('pages.forbiddenDescription')}</p><Link to="/">{t('navigation.dashboard')}</Link></section>
}
