import { useTranslation } from 'react-i18next'

export default function LoadingPage() {
  const { t } = useTranslation()
  return <p role="status">{t('app.loading')}</p>
}
