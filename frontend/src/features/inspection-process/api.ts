import { api } from '@/lib/auth/client'
import { requireOk } from '@/lib/auth/response'

/** Texte der Sachbearbeitung (GET /api/info-pages). Reiner Text mit Zeilenumbrüchen, leer = noch nicht gepflegt. */
export interface InspectionInfo {
  process: string
  consequences: string
  standards: string
  processEn: string
  consequencesEn: string
  standardsEn: string
}

export async function getInspectionInfo(signal: AbortSignal): Promise<InspectionInfo> {
  const response = requireOk(await api('/api/info-pages', { signal }), 'errors.infoPages')
  const { process, consequences, standards, processEn, consequencesEn, standardsEn } =
    (await response.json()) as InspectionInfo
  return {
    process: process ?? '',
    consequences: consequences ?? '',
    standards: standards ?? '',
    processEn: processEn ?? '',
    consequencesEn: consequencesEn ?? '',
    standardsEn: standardsEn ?? '',
  }
}
