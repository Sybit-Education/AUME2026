import { api } from '@/lib/auth/client'
import { requireOk } from '@/lib/auth/response'
import { UiError, type ErrorKey } from '@/lib/errors'

export interface InfoTexts {
  process: string
  consequences: string
  standards: string
}

export interface InfoPages extends InfoTexts {
  updatedAt: string | null
}

/** Fest hinterlegte englische Fassung. Sie wird beim Speichern unter den deutschen Text gehängt. */
export const ENGLISH: InfoTexts = {
  process: [
    '1. You will receive an announcement with the inspection period.',
    '2. The caretaker checks the common areas using the checklist. You are welcome to be present.',
    '3. You will receive the result with all defects in the app.',
    '4. If there are defects, a follow-up inspection with a deadline will take place.',
    '5. If a defect is not fixed, Seezeit may take further measures.',
  ].join('\n'),
  consequences: 'If there are defects, there will be a follow-up inspection with a deadline. If a defect remains, '
    + 'Seezeit may take further measures, for example cleaning by a cleaning company. The costs are based on Seezeit\'s rules.',
  standards: 'The cleanliness standards are the same for all residences. The caretaker uses exactly the same checklist as you.',
}

/** Deutsch und Englisch in einem Text, damit Bewohnende beide Sprachen sehen. */
export function withEnglish(name: keyof InfoTexts, german: string): string {
  return german.trim() === '' ? german : `${german}\n\n${ENGLISH[name]}`
}

/** Schneidet die angehängte englische Fassung wieder ab, damit links nur der deutsche Text steht. */
export function germanPart(name: keyof InfoTexts, stored: string): string {
  const suffix = `\n\n${ENGLISH[name]}`
  return stored.endsWith(suffix) ? stored.slice(0, -suffix.length) : stored
}

const saveErrors: Record<string, ErrorKey> = {
  info_text_required: 'errors.infoTextRequired',
  info_text_too_long: 'errors.infoTextTooLong',
}

export async function getInfoPages(signal: AbortSignal): Promise<InfoPages> {
  const response = requireOk(await api('/api/info-pages', { signal }), 'errors.infoPages')
  return (await response.json()) as InfoPages
}

export async function saveInfoPages(texts: InfoTexts, signal: AbortSignal): Promise<void> {
  const response = await api('/api/info-pages', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(texts),
    signal,
  })
  if (response.status === 400) {
    const body = (await response.json().catch(() => null)) as { code?: string } | null
    throw new UiError(saveErrors[body?.code ?? ''] ?? 'errors.infoPagesSave')
  }
  requireOk(response, 'errors.infoPagesSave')
}
