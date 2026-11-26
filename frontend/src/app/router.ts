import type { RouteObject } from 'react-router'
import AppLayout from './AppLayout'
import RequireAuth from './RequireAuth'
import RouteErrorLayout from './RouteErrorLayout'
import ForbiddenPage from './routes/ForbiddenPage'
import NotFoundPage from './routes/NotFoundPage'
import ComingSoonPage from './routes/ComingSoonPage'
import LoadingPage from '@/components/LoadingPage'
import { ensureSession } from '@/lib/auth/session'
import { authRoutes } from '@/features/auth/routes'
import { homeRoutes } from './homeRoute'
import { residenceRoutes } from '@/features/residences/routes'
import { inspectionPlanningRoutes } from '@/features/inspection-planning/routes'
import { inspectionOverviewRoutes } from '@/features/inspection-overview/routes'
import { inspectionRoutes } from '@/features/inspections/routes'
import { standardsRoutes } from '@/features/standards/routes'
import { checklistRoutes } from '@/features/checklist/routes'
import { moreRoutes } from '@/features/more/routes'

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
        children: [
          ...homeRoutes,
          ...residenceRoutes,
          ...standardsRoutes,
          ...inspectionRoutes,
          ...checklistRoutes,
          ...moreRoutes,
          ...inspectionPlanningRoutes,
          ...inspectionOverviewRoutes,
          // Platzhalter, bis das Putzplan-Feature (US-06/US-07) seine Route registriert.
          { path: 'putzplan', Component: ComingSoonPage },
          { path: 'forbidden', Component: ForbiddenPage },
        ],
      },
      { path: '*', Component: NotFoundPage },
    ],
  }]
}
