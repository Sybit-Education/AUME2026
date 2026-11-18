import { redirect, type RouteObject } from 'react-router'
import { protectedLoader } from '@/lib/auth/loaders'
import RequirePrivilege from '@/lib/auth/RequirePrivilege'
import ResidencesPage from './ResidencesPage'
import { listResidences } from './api'

export const residenceRoutes: RouteObject[] = [{
  path: 'residences',
  element: <RequirePrivilege privilege="RESIDENCE_READ"><ResidencesPage /></RequirePrivilege>,
  loader: protectedLoader('RESIDENCE_READ', ({ request }) => {
    const page = Number(new URL(request.url).searchParams.get('page') ?? '0')
    if (!Number.isSafeInteger(page) || page < 0) throw redirect('/residences')
    return listResidences(page, request.signal)
  }),
}]

export const residenceNavigation = {
  to: '/residences', label: 'navigation.residences', privilege: 'RESIDENCE_READ',
} as const
