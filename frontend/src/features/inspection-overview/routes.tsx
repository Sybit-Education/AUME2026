import type { RouteObject } from 'react-router'
import { protectedLoader } from '@/lib/auth/loaders'
import RequirePrivilege from '@/lib/auth/RequirePrivilege'
import { loadResidenceDetail } from './api'
import ResidenceInspectionsPage from './ResidenceInspectionsPage'

/** Die Übersicht selbst liegt im Tab „Übersicht“ (`/`), siehe `app/homeRoute.ts`. */
export const INSPECTION_OVERVIEW_PRIVILEGE = 'CLERK_INSPECTION_OVERVIEW_READ'

export const inspectionOverviewRoutes: RouteObject[] = [{
  path: 'inspection-overview/:residenceId',
  element: <RequirePrivilege privilege={INSPECTION_OVERVIEW_PRIVILEGE}><ResidenceInspectionsPage /></RequirePrivilege>,
  loader: protectedLoader(INSPECTION_OVERVIEW_PRIVILEGE, ({ request, params }) =>
    loadResidenceDetail(request, params.residenceId ?? '')),
}]
