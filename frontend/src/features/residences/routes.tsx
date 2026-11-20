import { redirect, type RouteObject } from 'react-router'
import { protectedLoader } from '@/lib/auth/loaders'
import RequirePrivilege from '@/lib/auth/RequirePrivilege'
import ResidencesPage from './ResidencesPage'
import { assignStaffAction } from './actions'
import { listCaretakers, listResidences, type ResidencePage, type StaffMember } from './api'

export interface ResidencesData {
  residences: ResidencePage
  // null, wenn der Nutzer die Zuständigkeit nicht ändern darf; das Backend prüft zusätzlich.
  caretakers: StaffMember[] | null
}

export const residenceRoutes: RouteObject[] = [{
  path: 'residences',
  element: <RequirePrivilege privilege="RESIDENCE_READ"><ResidencesPage /></RequirePrivilege>,
  loader: protectedLoader('RESIDENCE_READ', async ({ request }, user): Promise<ResidencesData> => {
    const page = Number(new URL(request.url).searchParams.get('page') ?? '0')
    if (!Number.isSafeInteger(page) || page < 0) throw redirect('/residences')
    const canAssign = user.privileges.includes('RESIDENCE_ASSIGN_STAFF')
    const [residences, caretakers] = await Promise.all([
      listResidences(page, request.signal),
      canAssign ? listCaretakers(request.signal) : Promise.resolve(null),
    ])
    return { residences, caretakers }
  }),
  action: assignStaffAction,
}]

export const residenceNavigation = {
  to: '/residences', label: 'navigation.residences', privilege: 'RESIDENCE_READ',
} as const
