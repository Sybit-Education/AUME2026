import { redirect, type RouteObject } from 'react-router'
import { protectedLoader } from '@/lib/auth/loaders'
import InspectionDetailsPage from './InspectionDetailsPage'
import { loadAppointment } from './api'

export const studentInspectionRoutes: RouteObject[] = [{
  path: 'student/inspections',
  Component: InspectionDetailsPage,
  loader: protectedLoader(undefined, async ({ request }, user) => {
    if (!user.roles.includes('STUDENT')) throw redirect('/forbidden')
    return { appointment: await loadAppointment(user, request.signal) }
  }),
}]
