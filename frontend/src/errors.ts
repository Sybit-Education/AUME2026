import type de from './locales/de.json'

export type ErrorKey = `errors.${keyof typeof de.errors}`

// Keep keys rather than translated strings so visible errors follow language changes.
export class UiError extends Error {
  readonly key: ErrorKey

  constructor(key: ErrorKey) {
    super(key)
    this.name = 'UiError'
    this.key = key
  }
}

export function errorKey(cause: unknown, fallback: ErrorKey): ErrorKey {
  return cause instanceof UiError ? cause.key : fallback
}
