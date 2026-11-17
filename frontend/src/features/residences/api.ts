import { api } from '../../auth'
import { requireOk } from '../../auth/response'

export interface ResidencePage {
  content: { id: number; name: string }[]
  page: { number: number; size: number; totalElements: number; totalPages: number }
}

export async function listResidences(page: number, signal: AbortSignal): Promise<ResidencePage> {
  const response = requireOk(await api(`/api/residences?page=${page}&size=20`, { signal }), 'errors.residences')
  return (await response.json()) as ResidencePage
}
