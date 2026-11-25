import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import LanguageSwitch from '@/components/LanguageSwitch'

/** Mehr → Einstellungen → Sprache (US-26). */
export default function SettingsPage() {
  const { t } = useTranslation()
  return (
    <>
      <Link className="back" to="/student/more">‹ {t('more.back')}</Link>
      <div className="ch"><h2>{t('settings.title')}</h2></div>
      <section className="card" aria-labelledby="settings-language">
        <h3 id="settings-language">{t('settings.language')}</h3>
        <LanguageSwitch label={t('settings.language')} />
        <p className="mut xs settings-hint">{t('settings.languageHint')}</p>
      </section>
    </>
  )
}
