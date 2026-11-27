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

/** Die Objektbetreuung hat keine Wohnanlagen-Seite; ihre Anlagen stehen im Profil. */
export const HIDDEN_FOR_ROLE = 'CARETAKER'

function hiddenFor(user: LoggedInUser) {
  return user.roles.includes(HIDDEN_FOR_ROLE) && !user.privileges.includes('RESIDENCE_ASSIGN_STAFF')
}

export const residenceRoutes: RouteObject[] = [{
  path: 'residences',
  element: <RequirePrivilege privilege="RESIDENCE_READ"><ResidencesPage /></RequirePrivilege>,
  loader: protectedLoader('RESIDENCE_READ', async ({ request }, user): Promise<ResidencesData> => {
    const page = Number(new URL(request.url).searchParams.get('page') ?? '0')
    if (hiddenFor(user)) throw redirect('/forbidden')
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
  to: '/residences', label: 'navigation.residences', privilege: 'RESIDENCE_READ', hiddenForRole: HIDDEN_FOR_ROLE,
} as const
