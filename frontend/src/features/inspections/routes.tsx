import { redirect, type RouteObject } from 'react-router'
import { protectedLoader } from '@/lib/auth/loaders'
import RequirePrivilege from '@/lib/auth/RequirePrivilege'
import InspectionsPage, { type InspectionsLoaderData } from './InspectionsPage'
import InspectionPage, { type InspectionLoaderData } from './InspectionPage'
import HistoryPage, { type HistoryLoaderData } from './HistoryPage'
import { getInspection, getPreviousFindings, listHistory, listInspections, startInspection } from './api'
import { historyQuery, invalidRange, readHistoryFilter } from './history'
import { inspectionRequestsAction, RESPOND_PRIVILEGE } from './requestsActions'
import { listRequests } from './requestsApi'
import { UiError } from '@/lib/errors'

const privilege = 'INSPECTION_PERFORM'

export const inspectionRoutes: RouteObject[] = [{
  path: 'inspections',
  element: <RequirePrivilege privilege={privilege}><InspectionsPage /></RequirePrivilege>,
  // Oben die Anfragen (#44, nur mit dem Recht zu antworten), darunter die anstehenden Kontrollen.
  loader: protectedLoader(privilege, async ({ request }, user): Promise<InspectionsLoaderData> => {
    const [inspections, requests] = await Promise.all([
      listInspections(request.signal),
      user.privileges.includes(RESPOND_PRIVILEGE) ? listRequests(request.signal) : Promise.resolve(null),
    ])
    return { inspections, requests }
  }),
  action: inspectionRequestsAction,
}, {
  path: 'inspections/history',
  element: <RequirePrivilege privilege={privilege}><HistoryPage /></RequirePrivilege>,
  loader: protectedLoader(privilege, async ({ request }): Promise<HistoryLoaderData> => {
    const filter = readHistoryFilter(new URL(request.url).searchParams)
    if (invalidRange(filter)) return { filter, data: null }
    return { filter, data: await listHistory(historyQuery(filter), request.signal) }
  }),
}, {
  // Früherer eigener Reiter für die Anfragen; sie stehen jetzt oben auf der Seite Kontrollen.
  path: 'inspections/requests',
  loader: ({ request }) => redirect(`/inspections${new URL(request.url).search}`),
}, {
  path: 'inspections/:id',
  element: <RequirePrivilege privilege={privilege}><InspectionPage /></RequirePrivilege>,
  loader: protectedLoader(privilege, async ({ params, request }): Promise<InspectionLoaderData> => {
    const id = Number(params.id)
    if (!Number.isSafeInteger(id) || id < 1) throw redirect('/inspections')
    // „Kontrolle starten“ in der Liste öffnet ?start=1: direkt starten und ohne den Parameter weiterleiten,
    // damit Neuladen oder Zurück nicht erneut startet. Klappt es nicht, öffnet die Seite mit dem Hinweis.
    let startError: InspectionLoaderData['startError']
    if (new URL(request.url).searchParams.get('start') === '1') {
      try {
        await startInspection(id)
      } catch (cause) {
        if (!(cause instanceof UiError)) throw cause
        startError = cause.key
      }
      if (!startError) throw redirect(`/inspections/${id}`)
    }
    const [inspection, previous] = await Promise.all([
      getInspection(id, request.signal), getPreviousFindings(id, request.signal),
    ])
    return { inspection, previous, startError }
  }),
}]

/** „Kontrollen“ bleibt bei einzelnen Kontrollen markiert, aber nicht in der Historie, die einen eigenen Reiter hat. */
export const inspectionNavigation = {
  to: '/inspections', label: 'navigation.inspections', privilege, inactiveWithin: ['/inspections/history/*'],
} as const

export const inspectionHistoryNavigation = {
  to: '/inspections/history', label: 'navigation.history', privilege,
} as const
