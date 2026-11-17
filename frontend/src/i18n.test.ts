import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import de from './locales/de.json'
import en from './locales/en.json'

function storage(initial: string | null = null) {
  let saved = initial
  return {
    getItem: vi.fn(() => saved),
    setItem: vi.fn((_key: string, value: string) => { saved = value }),
  }
}

function browser(languages: string[], preference = storage()) {
  vi.stubGlobal('window', { localStorage: preference })
  vi.stubGlobal('navigator', { languages })
  vi.stubGlobal('document', { documentElement: { lang: '' } })
  return preference
}

beforeEach(() => { vi.resetModules() })
afterEach(() => { vi.unstubAllGlobals() })

describe('language selection and switching', () => {
  it('uses a saved preference before browser preferences', async () => {
    browser(['en-US'], storage('de'))
    const { default: i18n } = await import('./i18n')
    expect(i18n.resolvedLanguage).toBe('de')
    expect(i18n.t('auth.login')).toBe('Anmelden')
    expect(document.documentElement.lang).toBe('de')
  })

  it('selects the first supported browser language, including regional variants', async () => {
    browser(['fr-FR', 'en-GB', 'de'])
    const { default: i18n } = await import('./i18n')
    expect(i18n.resolvedLanguage).toBe('en')
    expect(i18n.t('auth.login')).toBe('Sign in')
  })

  it('ignores unsupported stored preferences and falls back to German', async () => {
    browser(['fr'], storage('es'))
    const { default: i18n } = await import('./i18n')
    expect(i18n.resolvedLanguage).toBe('de')
  })

  it('updates the page language, persistence and an existing error key on switching', async () => {
    const preference = browser(['de'])
    const { default: i18n, languageStorageKey } = await import('./i18n')
    const { UiError } = await import('./errors')
    const error = new UiError('errors.login')
    expect(i18n.t(error.key)).toBe(de.errors.login)
    await i18n.changeLanguage('en')
    expect(i18n.t(error.key)).toBe(en.errors.login)
    expect(document.documentElement.lang).toBe('en')
    expect(preference.setItem).toHaveBeenLastCalledWith(languageStorageKey, 'en')
    vi.resetModules()
    const restored = await import('./i18n')
    expect(restored.default.resolvedLanguage).toBe('en')
  })

  it('works when browser storage is blocked', async () => {
    browser(['en'])
    vi.stubGlobal('window', {
      get localStorage() { throw new Error('Storage is blocked') },
    })
    const { default: i18n } = await import('./i18n')
    expect(i18n.resolvedLanguage).toBe('en')
    await expect(i18n.changeLanguage('de')).resolves.toBeDefined()
    expect(document.documentElement.lang).toBe('de')
  })

  it('falls back to German when an English resource is missing', async () => {
    browser(['en'])
    const { default: i18n } = await import('./i18n')
    i18n.removeResourceBundle('en', 'translation')
    expect(i18n.t('auth.login')).toBe('Anmelden')
  })
})

function paths(value: Record<string, unknown>, prefix = ''): string[] {
  return Object.entries(value).flatMap(([key, child]) => {
    const path = prefix ? `${prefix}.${key}` : key
    return typeof child === 'object' && child !== null
      ? paths(child as Record<string, unknown>, path)
      : [path]
  }).sort()
}

it('provides the same non-empty translation keys in German and English', () => {
  expect(paths(de)).toEqual(paths(en))
  function nonEmpty(value: Record<string, unknown>) {
    for (const child of Object.values(value)) {
      if (typeof child === 'object' && child !== null) nonEmpty(child as Record<string, unknown>)
      else expect(typeof child === 'string' && child.trim().length > 0).toBe(true)
    }
  }
  nonEmpty(de)
  nonEmpty(en)
})
