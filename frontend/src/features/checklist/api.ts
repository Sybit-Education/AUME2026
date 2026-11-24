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
