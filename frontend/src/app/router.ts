import type { RouteObject } from 'react-router'
import AppLayout from './AppLayout'
import RequireAuth from './RequireAuth'
import RouteErrorLayout from './RouteErrorLayout'
import LoginPage from '../pages/LoginPage'
import ForbiddenPage from '../pages/ForbiddenPage'
import NotFoundPage from '../pages/NotFoundPage'
import LoadingPage from '../pages/LoadingPage'
import { ensureSession } from '../auth/session'
import { dashboardRoutes } from '../features/dashboard/routes'
import { residenceRoutes } from '../features/residences/routes'

export function createAppRoutes(): RouteObject[] {
  return [{
    id: 'root',
    path: '/',
    Component: AppLayout,
    ErrorBoundary: RouteErrorLayout,
    HydrateFallback: LoadingPage,
    loader: ensureSession,
    children: [
      { path: 'login', Component: LoginPage },
      {
        Component: RequireAuth,
        children: [...dashboardRoutes, ...residenceRoutes, { path: 'forbidden', Component: ForbiddenPage }],
      },
      { path: '*', Component: NotFoundPage },
    ],
  }]
}
