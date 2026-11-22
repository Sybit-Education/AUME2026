import { redirect, type RouteObject } from 'react-router'
import { protectedLoader } from '@/lib/auth/loaders'
import RequirePrivilege from '@/lib/auth/RequirePrivilege'
import InspectionsPage from './InspectionsPage'
import InspectionPage from './InspectionPage'
import { getInspection, listInspections } from './api'

const privilege = 'INSPECTION_PERFORM'

export const inspectionRoutes: RouteObject[] = [{
  path: 'inspections',
  element: <RequirePrivilege privilege={privilege}><InspectionsPage /></RequirePrivilege>,
  loader: protectedLoader(privilege, ({ request }) => {
    const page = Number(new URL(request.url).searchParams.get('page') ?? '0')
    if (!Number.isSafeInteger(page) || page < 0) throw redirect('/inspections')
    return listInspections(page, request.signal)
  }),
}, {
  path: 'inspections/:id',
  element: <RequirePrivilege privilege={privilege}><InspectionPage /></RequirePrivilege>,
  loader: protectedLoader(privilege, ({ params, request }) => {
    const id = Number(params.id)
    if (!Number.isSafeInteger(id) || id < 1) throw redirect('/inspections')
    return getInspection(id, request.signal)
  }),
}]

export const inspectionNavigation = {
  to: '/inspections', label: 'navigation.inspections', privilege,
} as const
