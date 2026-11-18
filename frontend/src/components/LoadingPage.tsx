import { useTranslation } from 'react-i18next'

export default function LoadingPage() {
  const { t } = useTranslation()
  return <p className="mn" role="status"><span className="loader" aria-hidden="true" /> {t('app.loading')}</p>
}
