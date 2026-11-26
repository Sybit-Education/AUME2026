import type { RouteObject } from 'react-router'
import { protectedLoader } from '@/lib/auth/loaders'
import { loadDashboard } from '@/features/dashboard/api'
import { loadOverview } from '@/features/inspection-overview/api'
import { INSPECTION_OVERVIEW_PRIVILEGE } from '@/features/inspection-overview/routes'
import HomePage from './HomePage'

export const homeRoutes: RouteObject[] = [{
  index: true,
  Component: HomePage,
  loader: protectedLoader<unknown>(undefined, ({ request }, user) => user.privileges.includes(INSPECTION_OVERVIEW_PRIVILEGE)
    ? loadOverview(request)
    : loadDashboard(user, request.signal)),
}]
