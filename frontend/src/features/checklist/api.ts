import { api } from '@/lib/auth/client'
import { requireOk } from '@/lib/auth/response'

export interface Criterion {
  id: number
  title: string
  description: string
}

export interface ChecklistArea {
  name: string
  criteria: Criterion[]
}

export interface Checklist {
  version: number
  areas: ChecklistArea[]
}

/**
 * Aktuell veröffentlichte Checkliste in der gewählten Sprache; die Sprache sendet api() als
 * Accept-Language mit. null, solange Seezeit noch keine Checkliste veröffentlicht hat.
 */
export async function getChecklist(signal: AbortSignal): Promise<Checklist | null> {
  const response = await api('/api/checklist', { signal })
  if (response.status === 404) return null
  return (await requireOk(response, 'errors.checklist').json()) as Checklist
}

/** Abgehakte Kriterien im Selbstcheck der eigenen WG (US-84). */
export interface SelfCheck {
  checkedCriterionIds: number[]
}

/** null, wenn die Person keiner WG angehört (404 not_member). */
export async function getSelfCheck(signal: AbortSignal): Promise<SelfCheck | null> {
  const response = await api('/api/wg/self-check', { signal })
  if (response.status === 404) return null
  return (await requireOk(response, 'errors.selfCheck').json()) as SelfCheck
}

export async function setSelfCheck(criterionId: number, done: boolean, signal: AbortSignal): Promise<void> {
  requireOk(await api(`/api/wg/self-check/${criterionId}`, { method: done ? 'PUT' : 'DELETE', signal }),
    'errors.selfCheckSave')
}

export async function resetSelfCheck(signal: AbortSignal): Promise<void> {
  requireOk(await api('/api/wg/self-check', { method: 'DELETE', signal }), 'errors.selfCheckSave')
}
