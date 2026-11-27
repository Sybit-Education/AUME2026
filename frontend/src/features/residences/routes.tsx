import { redirect, type RouteObject } from 'react-router'
import type { LoggedInUser } from '@/lib/auth/client'
import { protectedLoader } from '@/lib/auth/loaders'
import RequirePrivilege from '@/lib/auth/RequirePrivilege'
import ResidencesPage from './ResidencesPage'
import { assignStaffAction } from './actions'
import {
  listCaretakers, listResidences, type Residence, type ResidencePage, type ResidenceSummaryPage, type StaffMember,
} from './api'

// Sachbearbeitung: Tabelle mit Zuständigkeit. Alle anderen: bisherige Namensliste.
export type ResidencesData =
  | { kind: 'clerk'; residences: ResidencePage; caretakers: StaffMember[] }
  | { kind: 'summary'; residences: ResidenceSummaryPage }

/**
 * Die Objektbetreuung hat keine Wohnanlagen-Seite, ihre Anlagen stehen im Profil. Wer zusätzlich Zuständigkeiten
 * pflegen darf (Sachbearbeitung), sieht sie trotzdem.
 */
export function showsResidences(user: LoggedInUser) {
  return !user.roles.includes('CARETAKER') || user.privileges.includes('RESIDENCE_ASSIGN_STAFF')
}

export const residenceRoutes: RouteObject[] = [{
  path: 'residences',
  element: <RequirePrivilege privilege="RESIDENCE_READ"><ResidencesPage /></RequirePrivilege>,
  loader: protectedLoader('RESIDENCE_READ', async ({ request }, user): Promise<ResidencesData> => {
    if (!showsResidences(user)) throw redirect('/forbidden')
    const page = Number(new URL(request.url).searchParams.get('page') ?? '0')
    if (!Number.isSafeInteger(page) || page < 0) throw redirect('/residences')
    if (!user.privileges.includes('RESIDENCE_ASSIGN_STAFF')) {
      return { kind: 'summary', residences: await listResidences(page, request.signal) }
    }
    const [residences, caretakers] = await Promise.all([
      listResidences<Residence>(page, request.signal),
      listCaretakers(request.signal),
    ])
    return { kind: 'clerk', residences, caretakers }
  }),
  action: assignStaffAction,
}]

export const residenceNavigation = {
  to: '/residences', label: 'navigation.residences', privilege: 'RESIDENCE_READ', visibleFor: showsResidences,
} as const
