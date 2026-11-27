import { redirect, type RouteObject } from 'react-router'
import { protectedLoader } from '@/lib/auth/loaders'
import RequirePrivilege from '@/lib/auth/RequirePrivilege'
import InspectionPage, { type InspectionLoaderData } from './InspectionPage'
import HistoryPage, { type HistoryLoaderData } from './HistoryPage'
import { getInspection, getPreviousFindings, listHistory, startInspection } from './api'
import { historyQuery, invalidRange, readHistoryFilter } from './history'
import { UiError } from '@/lib/errors'

const privilege = 'INSPECTION_PERFORM'

// Die frühere Seite Kontrollen ist in der Übersicht (Startseite) aufgegangen: dieselben Tabellen und der
// Antwort-Dialog. Alte Adressen, auch mit ?answer= oder ?counter=, führen dorthin.
const toOverview = ({ request }: { request: Request }) => redirect(`/${new URL(request.url).search}`)

export const inspectionRoutes: RouteObject[] = [{
  path: 'inspections',
  loader: toOverview,
}, {
  path: 'inspections/history',
  element: <RequirePrivilege privilege={privilege}><HistoryPage /></RequirePrivilege>,
  loader: protectedLoader(privilege, async ({ request }): Promise<HistoryLoaderData> => {
    const filter = readHistoryFilter(new URL(request.url).searchParams)
    if (invalidRange(filter)) return { filter, data: null }
    return { filter, data: await listHistory(historyQuery(filter), request.signal) }
  }),
}, {
  // Früherer eigener Reiter für die Anfragen.
  path: 'inspections/requests',
  loader: toOverview,
}, {
  path: 'inspections/:id',
  element: <RequirePrivilege privilege={privilege}><InspectionPage /></RequirePrivilege>,
  loader: protectedLoader(privilege, async ({ params, request }): Promise<InspectionLoaderData> => {
    const id = Number(params.id)
    if (!Number.isSafeInteger(id) || id < 1) throw redirect('/')
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

export const inspectionHistoryNavigation = {
  to: '/inspections/history', label: 'navigation.history', privilege,
} as const
