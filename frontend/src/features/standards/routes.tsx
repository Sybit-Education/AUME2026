import type { RouteObject } from 'react-router'
import { protectedLoader } from '@/lib/auth/loaders'
import RequirePrivilege from '@/lib/auth/RequirePrivilege'
import InfoPagesPage from './InfoPagesPage'
import { saveInfoPagesAction } from './actions'
import { getInfoPages } from './api'

export const standardsRoutes: RouteObject[] = [{
  path: 'standards/info',
  element: <RequirePrivilege privilege="STANDARDS_VIEW"><InfoPagesPage /></RequirePrivilege>,
  loader: protectedLoader('STANDARDS_VIEW', ({ request }) => getInfoPages(request.signal)),
  action: saveInfoPagesAction,
}]

export const standardsNavigation = {
  to: '/standards/info', label: 'navigation.standards', privilege: 'STANDARDS_VIEW',
} as const
