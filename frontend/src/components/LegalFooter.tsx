import { useTranslation } from 'react-i18next'

const imprintUrl = 'https://www.htwg-konstanz.de/info/impressum'
const privacyUrl = 'https://www.htwg-konstanz.de/datenschutzerklaerung'

/** Impressum und Datenschutzerklärung der HTWG Konstanz, unten in der Ansicht. */
export default function LegalFooter() {
  const { t } = useTranslation()
  return (
    <footer className="legal-footer">
      <nav aria-label={t('legal.nav')}>
        <a href={imprintUrl} target="_blank" rel="noopener noreferrer">
          {t('legal.imprint')}
          <span className="sr-only"> ({t('legal.newTab')})</span>
        </a>
        <a href={privacyUrl} target="_blank" rel="noopener noreferrer">
          {t('legal.privacy')}
          <span className="sr-only"> ({t('legal.newTab')})</span>
        </a>
      </nav>
      <p className="mut xs">{t('legal.provider')}</p>
    </footer>
  )
}
