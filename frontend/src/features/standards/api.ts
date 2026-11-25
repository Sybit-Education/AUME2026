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
