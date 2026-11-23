import { api } from '@/lib/auth/client'
import { requireOk } from '@/lib/auth/response'
import { UiError } from '@/lib/errors'

export type InspectionStatus = 'PLANNED' | 'DRAFT' | 'FINALIZED' | 'PUBLISHED'
export type OverallResult = 'PASSED' | 'FAILED'
export type Rating = 'OK' | 'DEFICIENT' | 'RESOLVED_ON_SITE'
export type Severity = 'LIGHT' | 'SEVERE'

export interface InspectionSummary {
  id: number
  flatLabel: string
  status: InspectionStatus
  round: number
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

export interface FindingInput {
  rating: Rating
  severity?: Severity | null
  note?: string | null
  buildingCauseSuspected?: boolean
}

const json = { 'Content-Type': 'application/json' }

export async function listInspections(page: number, signal: AbortSignal): Promise<InspectionPage> {
  const response = requireOk(await api(`/api/inspections?page=${page}&size=20`, { signal }), 'errors.inspections')
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

async function change(path: string, method: 'POST' | 'PUT' | 'DELETE', body?: unknown): Promise<Inspection> {
  const raw = await api(path, {
    method,
    headers: body === undefined ? undefined : json,
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  // 409: Die Kontrolle wurde inzwischen anderweitig geändert (gestartet, abgeschlossen, …).
  if (raw.status === 409) throw new UiError('errors.inspectionConflict')
  const response = requireOk(raw, 'errors.inspectionSave')
  return (await response.json()) as Inspection
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
