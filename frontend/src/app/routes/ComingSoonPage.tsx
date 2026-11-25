import { useTranslation } from 'react-i18next'

/** Platzhalter für Navigationsziele, deren Feature noch entsteht (z. B. Putzplan, US-06/US-07). */
export default function ComingSoonPage() {
  const { t } = useTranslation()
  return (
    <section className="card empty">
      <h4>{t('pages.comingSoon')}</h4>
      <p className="mut">{t('pages.comingSoonDescription')}</p>
    </section>
  )
}
