import { api } from '@/lib/auth/client'
import { requireOk } from '@/lib/auth/response'
import { UiError, type ErrorKey } from '@/lib/errors'

export interface StaffMember {
  id: number
  name: string
}

// Bisherige Antwort für alle Sichten außer der Sachbearbeitung.
export interface ResidenceSummary {
  id: number
  name: string
}

// Antwort für die Sachbearbeitung.
export interface Residence extends ResidenceSummary {
  flats: string[]
  caretaker: StaffMember | null
  deputy: StaffMember | null
}

interface Page<T> {
  content: T[]
  page: { number: number; size: number; totalElements: number; totalPages: number }
}

export type ResidencePage = Page<Residence>
export type ResidenceSummaryPage = Page<ResidenceSummary>
export type CaretakerPage = Page<StaffMember>

export async function listResidences<T extends ResidenceSummary>(page: number, signal: AbortSignal): Promise<Page<T>> {
  const response = requireOk(await api(`/api/residences?page=${page}&size=20`, { signal }), 'errors.residences')
  return (await response.json()) as Page<T>
}

// Auswahlliste für Betreuung und Vertretung. Das Backend begrenzt eine Seite auf 100 Einträge.
export async function listCaretakers(signal: AbortSignal): Promise<StaffMember[]> {
  const response = requireOk(await api('/api/caretakers?page=0&size=100', { signal }), 'errors.caretakers')
  return ((await response.json()) as CaretakerPage).content
}

// Stabile Fehlercodes des Backends für fachlich ungültige Eingaben.
const staffErrors: Record<string, ErrorKey> = {
  caretaker_required: 'errors.caretakerRequired',
  unknown_caretaker: 'errors.unknownCaretaker',
  deputy_must_differ: 'errors.deputyMustDiffer',
}

async function staffErrorKey(response: Response): Promise<ErrorKey> {
  try {
    const body = (await response.json()) as { code?: unknown }
    return (typeof body.code === 'string' && staffErrors[body.code]) || 'errors.assignStaff'
  } catch {
    return 'errors.assignStaff'
  }
}

export async function assignStaff(
  residenceId: number,
  caretakerId: number,
  deputyId: number | null,
  signal: AbortSignal,
): Promise<void> {
  const response = await api(`/api/residences/${residenceId}/staff`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ caretakerId, deputyId }),
    signal,
  })
  if (response.status === 400) throw new UiError(await staffErrorKey(response))
  requireOk(response, 'errors.assignStaff')
}
