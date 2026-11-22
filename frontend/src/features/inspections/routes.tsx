import { redirect, type RouteObject } from 'react-router'
import { protectedLoader } from '@/lib/auth/loaders'
import RequirePrivilege from '@/lib/auth/RequirePrivilege'
import InspectionsPage from './InspectionsPage'
import InspectionPage, { type InspectionLoaderData } from './InspectionPage'
import HistoryPage, { type HistoryLoaderData } from './HistoryPage'
import { getInspection, getPreviousFindings, listHistory, listInspections } from './api'
import { historyQuery, invalidRange, readHistoryFilter } from './history'

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
  path: 'inspections/history',
  element: <RequirePrivilege privilege={privilege}><HistoryPage /></RequirePrivilege>,
  loader: protectedLoader(privilege, async ({ request }): Promise<HistoryLoaderData> => {
    const filter = readHistoryFilter(new URL(request.url).searchParams)
    if (invalidRange(filter)) return { filter, data: null }
    return { filter, data: await listHistory(historyQuery(filter), request.signal) }
  }),
}, {
  path: 'inspections/:id',
  element: <RequirePrivilege privilege={privilege}><InspectionPage /></RequirePrivilege>,
  loader: protectedLoader(privilege, async ({ params, request }): Promise<InspectionLoaderData> => {
    const id = Number(params.id)
    if (!Number.isSafeInteger(id) || id < 1) throw redirect('/inspections')
    const [inspection, previous] = await Promise.all([
      getInspection(id, request.signal), getPreviousFindings(id, request.signal),
    ])
    return { inspection, previous }
  }),
}]

export const inspectionNavigation = {
  to: '/inspections', label: 'navigation.inspections', privilege,
} as const
