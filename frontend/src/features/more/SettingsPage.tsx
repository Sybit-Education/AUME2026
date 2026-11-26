import { useTranslation } from 'react-i18next'
import LanguageSwitch from '@/components/LanguageSwitch'

/** Mehr → Einstellungen → Sprache (US-26). */
export default function SettingsPage() {
  const { t } = useTranslation()
  return (
    <section className="card" aria-labelledby="settings-language">
      <h3 id="settings-language">{t('settings.language')}</h3>
      <LanguageSwitch label={t('settings.language')} />
      <p className="mut xs settings-hint">{t('settings.languageHint')}</p>
    </section>
  )
}
