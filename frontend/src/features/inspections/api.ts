import { api } from '@/lib/auth/client'
import { requireOk } from '@/lib/auth/response'
import { UiError, type ErrorKey } from '@/lib/errors'

export type InspectionStatus = 'PLANNED' | 'DRAFT' | 'FINALIZED' | 'PUBLISHED'
export type OverallResult = 'PASSED' | 'FAILED'
export type Rating = 'OK' | 'DEFICIENT' | 'RESOLVED_ON_SITE'
export type Severity = 'LIGHT' | 'SEVERE'

export interface InspectionSummary {
  id: number
  flatLabel: string
  residenceName: string
  status: InspectionStatus
  round: number
  /** Angekündigter Zeitraum und Uhrzeit; leer bei Kontrollen ohne Zeitraum, z. B. Nachkontrollen. */
  startsOn: string | null
  endsOn: string | null
  windowStart: string | null
  windowEnd: string | null
  /** Leer bei Kontrollen ohne Zeitraum. `INDIVIDUAL` = nur einzelne WGs (US-18b). */
  periodKind: 'REGULAR' | 'INDIVIDUAL' | null
}

export interface InspectionPage {
  content: InspectionSummary[]
  page: { number: number; size: number; totalElements: number; totalPages: number }
}

export interface Criterion {
  id: number
  area: string
  title: string
  description: string
  position: number
}

export interface Finding {
  criterionId: number
  rating: Rating
  severity: Severity | null
  note: string | null
  buildingCauseSuspected: boolean
  /** Fotos zum Mangel (US-35); fehlt bei älteren Antworten. */
  photoIds?: number[]
}

export interface Inspection {
  id: number
  flatLabel: string
  residenceName: string
  capacity: number
  round: number
  status: InspectionStatus
  overallResult: OverallResult | null
  inspectedAt: string | null
  inspectorName: string | null
  criteria: Criterion[]
  findings: Finding[]
}

export interface HistoryEntry {
  id: number
  flatLabel: string
  residenceName: string
  inspectedAt: string | null
  round: number
  status: InspectionStatus
  overallResult: OverallResult | null
  deficiencies: number
  inspectorName: string | null
}

export interface HistoryPage {
  content: HistoryEntry[]
  page: { number: number; size: number; totalElements: number; totalPages: number }
}

/** Mangel oder vor Ort geklärter Befund aus einer früheren Kontrolle derselben WG (US-51). */
export interface PreviousDefect {
  area: string
  criterionTitle: string
  rating: Exclude<Rating, 'OK'>
  severity: Severity | null
  note: string | null
  buildingCauseSuspected: boolean
}

export interface PreviousInspection {
  id: number
  inspectedAt: string | null
  round: number
  overallResult: OverallResult | null
  defects: PreviousDefect[]
}

export interface FindingInput {
  rating: Rating
  severity?: Severity | null
  note?: string | null
  buildingCauseSuspected?: boolean
}

const json = { 'Content-Type': 'application/json' }

/** Alle anstehenden Kontrollen auf einmal (höchstens 100); die Tabelle scrollt ab fünf Zeilen. */
export async function listInspections(signal: AbortSignal): Promise<InspectionPage> {
  const response = requireOk(await api('/api/inspections?size=100', { signal }), 'errors.inspections')
  return (await response.json()) as InspectionPage
}

/** `query` ist bereits geprüft, siehe `historyQuery` in history.ts. */
export async function listHistory(query: URLSearchParams, signal: AbortSignal): Promise<HistoryPage> {
  const response = requireOk(await api(`/api/inspections/history?${query}`, { signal }), 'errors.history')
  return (await response.json()) as HistoryPage
}

export async function getInspection(id: number, signal: AbortSignal): Promise<Inspection> {
  const response = requireOk(await api(`/api/inspections/${id}`, { signal }), 'errors.inspection')
  return (await response.json()) as Inspection
}

/**
 * Lädt das Protokoll einer abgeschlossenen Kontrolle als PDF und speichert es über den Browser. Läuft über
 * `api`, damit Sprache, Anmeldung und Fehlerbehandlung wie bei allen anderen Aufrufen gelten.
 */
export async function downloadProtocol(id: number): Promise<void> {
  const response = requireOk(await api(`/api/inspections/${id}/protocol.pdf`), 'errors.protocolDownload')
  const fileName = /filename="([^"]+)"/.exec(response.headers.get('Content-Disposition') ?? '')?.[1]
    ?? `Kontrollprotokoll_${id}.pdf`
  const url = URL.createObjectURL(await response.blob())
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.append(link)
  link.click()
  link.remove()
  // Erst nach dem Klick freigeben, sonst bricht der Download in manchen Browsern ab.
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

/** Höchstens drei abgeschlossene frühere Kontrollen der WG, neueste zuerst. */
export async function getPreviousFindings(id: number, signal: AbortSignal): Promise<PreviousInspection[]> {
  const response = requireOk(await api(`/api/inspections/${id}/previous-findings`, { signal }), 'errors.inspection')
  return (await response.json()) as PreviousInspection[]
}

async function change(path: string, method: 'POST' | 'PUT' | 'DELETE', body?: unknown): Promise<Inspection> {
  const raw = await api(path, {
    method,
    headers: body === undefined ? undefined : json,
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  // 409: Die Kontrolle wurde inzwischen anderweitig geändert (gestartet, abgeschlossen, …), oder ihr Zeitraum
  // hat noch nicht begonnen.
  if (raw.status === 409) {
    const error = (await raw.json().catch(() => null)) as { code?: string } | null
    throw new UiError(error?.code === 'period_not_started' ? 'errors.inspectionNotStarted' : 'errors.inspectionConflict')
  }
  // 400 bei Fotos: eigene Meldung je Grund statt „konnte nicht gespeichert werden“ (US-35).
  if (raw.status === 400) {
    const error = (await raw.clone().json().catch(() => null)) as { code?: string } | null
    const key = error?.code ? photoErrors[error.code] : undefined
    if (key) throw new UiError(key)
  }
  const response = requireOk(raw, 'errors.inspectionSave')
  return (await response.json()) as Inspection
}

const photoErrors: Record<string, ErrorKey> = {
  too_many_photos: 'errors.photoTooMany',
  not_an_image: 'errors.photoType',
  photo_needs_defect: 'errors.photoNeedsDefect',
}

export const startInspection = (id: number) => change(`/api/inspections/${id}/start`, 'POST')

export const rateCriterion = (id: number, criterionId: number, input: FindingInput) =>
  change(`/api/inspections/${id}/findings/${criterionId}`, 'PUT', input)

export const clearFinding = (id: number, criterionId: number) =>
  change(`/api/inspections/${id}/findings/${criterionId}`, 'DELETE')

/** Ohne Bereich setzt das Backend alle noch unbewerteten Kriterien auf OK. */
export const markOk = (id: number, area?: string) =>
  change(`/api/inspections/${id}/mark-ok`, 'POST', area === undefined ? undefined : { area })

export const finalizeInspection = (id: number, overallResult: OverallResult) =>
  change(`/api/inspections/${id}/finalize`, 'POST', { overallResult })

/** Höchstzahl der Fotos je Mangel, wie im Backend (US-35). */
export const MAX_PHOTOS = 5
const MAX_PHOTO_BYTES = 10 * 1024 * 1024
/** Erlaubte Formate, wie im Backend: nur Rasterbilder, kein SVG. */
export const PHOTO_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'image/heic', 'image/heif']

export function isPhotoFile(file: File): boolean {
  return PHOTO_TYPES.includes(file.type.toLowerCase())
}

/**
 * Lädt ein Foto in die Entwicklungsablage (`POST /api/attachments`, #79) und verknüpft es mit dem Mangel des
 * Kriteriums (US-35).
 */
export async function addPhoto(id: number, criterionId: number, file: File): Promise<Inspection> {
  if (!isPhotoFile(file)) throw new UiError('errors.photoType')
  if (file.size > MAX_PHOTO_BYTES) throw new UiError('errors.photoTooLarge')
  const form = new FormData()
  form.append('file', file)
  const uploaded = requireOk(await api('/api/attachments', { method: 'POST', body: form }), 'errors.photoUpload')
  const { id: attachmentId } = (await uploaded.json()) as { id: number }
  return change(`/api/inspections/${id}/findings/${criterionId}/photos`, 'POST', { attachmentId })
}

export const removePhoto = (id: number, criterionId: number, photoId: number) =>
  change(`/api/inspections/${id}/findings/${criterionId}/photos/${photoId}`, 'DELETE')

/** Lädt ein Foto über `api`, damit auch die Anmeldung per Token gilt, und liefert eine Objekt-URL. */
export async function loadPhoto(id: number, photoId: number, signal: AbortSignal): Promise<string> {
  const response = requireOk(await api(`/api/inspections/${id}/photos/${photoId}`, { signal }), 'errors.photoLoad')
  return URL.createObjectURL(await response.blob())
}
