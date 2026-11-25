import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { supportedLanguage } from '@/lib/i18n'

const languages = [
  { code: 'de', name: 'Deutsch' },
  { code: 'en', name: 'English' },
] as const

/**
 * Umschalter Deutsch/English (US-26). Nutzt die gemeinsame i18next-Instanz; dort wird die Auswahl
 * gespeichert und das Attribut lang gesetzt. Das Layout lädt danach die Daten der Seite neu.
 */
export default function LanguageSwitch({ label }: { label: string }) {
  const { t, i18n } = useTranslation()
  const current = supportedLanguage(i18n.resolvedLanguage) ?? 'de'
  const [failed, setFailed] = useState(false)

  async function choose(code: string) {
    if (code === current) return
    setFailed(false)
    try { await i18n.changeLanguage(code) } catch { setFailed(true) }
  }

  return (
    <>
      <div className="lang-switch" role="group" aria-label={label}>
        {languages.map(({ code, name }) => (
          <button
            key={code}
            type="button"
            lang={code}
            className={code === current ? 'btn sm' : 'btn sm gh'}
            aria-pressed={code === current}
            onClick={() => void choose(code)}
          >
            {name}
          </button>
        ))}
      </div>
      {failed && <p className="err" role="alert">{t('errors.language')}</p>}
    </>
  )
}
