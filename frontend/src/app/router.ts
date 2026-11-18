import type { RouteObject } from 'react-router'
import AppLayout from './AppLayout'
import RequireAuth from './RequireAuth'
import RouteErrorLayout from './RouteErrorLayout'
import ForbiddenPage from './routes/ForbiddenPage'
import NotFoundPage from './routes/NotFoundPage'
import LoadingPage from '@/components/LoadingPage'
import { ensureSession } from '@/lib/auth/session'
import { authRoutes } from '@/features/auth/routes'
import { dashboardRoutes } from '@/features/dashboard/routes'
import { residenceRoutes } from '@/features/residences/routes'

export function createAppRoutes(): RouteObject[] {
  return [{
    id: 'root',
    path: '/',
    Component: AppLayout,
    ErrorBoundary: RouteErrorLayout,
    HydrateFallback: LoadingPage,
    loader: ensureSession,
    children: [
      ...authRoutes,
      {
        Component: RequireAuth,
        children: [...dashboardRoutes, ...residenceRoutes, { path: 'forbidden', Component: ForbiddenPage }],
      },
      { path: '*', Component: NotFoundPage },
    ],
  }]
}
