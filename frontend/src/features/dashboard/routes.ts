import type { RouteObject } from 'react-router'
import { protectedLoader } from '../../app/loaders'
import { loadDashboard } from './api'
import DashboardPage from './DashboardPage'

export const dashboardRoutes: RouteObject[] = [{
  index: true,
  Component: DashboardPage,
  loader: protectedLoader(undefined, ({ request }, user) => loadDashboard(user, request.signal)),
}]
