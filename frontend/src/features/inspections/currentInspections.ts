import type { LoggedInUser } from '@/lib/auth/client'
import { listInspections, type InspectionPage } from './api'
import { RESPOND_PRIVILEGE } from './requestsActions'
import { listRequests, type RequestPage } from './requestsApi'

/** Daten der Seite Kontrollen; die Übersicht der Objektbetreuung (#41) zeigt dieselben Tabellen. */
export interface CurrentInspections {
  inspections: InspectionPage
  /** null ohne das Recht, Anfragen zu beantworten */
  requests: RequestPage | null
}

export async function loadCurrentInspections(user: LoggedInUser, signal: AbortSignal): Promise<CurrentInspections> {
  const [inspections, requests] = await Promise.all([
    listInspections(signal),
    user.privileges.includes(RESPOND_PRIVILEGE) ? listRequests(signal) : Promise.resolve(null),
  ])
  return { inspections, requests }
}

/** Wer dieses Privileg hat, sieht auf der Startseite (`/`) die Übersicht der Objektbetreuung (#41). */
export const CARETAKER_OVERVIEW_PRIVILEGE = 'INSPECTION_OVERVIEW_READ'
