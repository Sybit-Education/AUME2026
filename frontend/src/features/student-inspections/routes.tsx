import { redirect, type RouteObject } from 'react-router'
import { protectedLoader } from '@/lib/auth/loaders'
import InspectionResultPage, { type InspectionResultData } from './InspectionResultPage'
import { loadAppointment, loadLatestResult } from './api'
import './inspection-result.css'

export const studentInspectionRoutes: RouteObject[] = [{
  // US-38: Reiter „Ergebnis“. Der Termin wird nur für den Leerzustand gebraucht.
  path: 'result',
  Component: InspectionResultPage,
  loader: protectedLoader(undefined, async ({ request }, user): Promise<InspectionResultData> => {
    if (!user.roles.includes('STUDENT')) throw redirect('/forbidden')
    const result = await loadLatestResult(request.signal)
    if (result) return { result, announced: false }
    return { result: null, announced: (await loadAppointment(user, request.signal)) !== null }
  }),
}]
