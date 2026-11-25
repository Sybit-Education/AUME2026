import { createInstance } from 'i18next'
import { initReactI18next } from 'react-i18next'
import de from '@/locales/de.json'
import en from '@/locales/en.json'

export const supportedLanguages = ['de', 'en'] as const
export type Language = typeof supportedLanguages[number]
export const languageStorageKey = 'aume.language'

export function supportedLanguage(value: string | null | undefined): Language | undefined {
  const base = value?.toLowerCase().split(/[-_]/)[0]
  return supportedLanguages.find((language) => language === base)
}

function initialLanguage(): Language {
  try {
    const stored = supportedLanguage(window.localStorage.getItem(languageStorageKey))
    if (stored) return stored
  } catch {
    // Storage may be unavailable; browser preferences and switching still work.
  }
  if (typeof navigator !== 'undefined') {
    for (const candidate of navigator.languages ?? [navigator.language]) {
      const language = supportedLanguage(candidate)
      if (language) return language
    }
  }
  return 'de'
}

const i18n = createInstance()

i18n.on('languageChanged', (value) => {
  const language = supportedLanguage(value) ?? 'de'
  if (typeof document !== 'undefined') document.documentElement.lang = language
  try {
    window.localStorage.setItem(languageStorageKey, language)
  } catch {
    // Persistence is optional, for example when browser storage is blocked.
  }
})

void i18n.use(initReactI18next).init({
  resources: { de: { translation: de }, en: { translation: en } },
  lng: initialLanguage(),
  supportedLngs: [...supportedLanguages],
  fallbackLng: 'de',
  load: 'languageOnly',
  initAsync: false,
  interpolation: { escapeValue: false },
})

export default i18n
