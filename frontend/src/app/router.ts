import { createElement } from 'react'
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
import { dashboardRoutes } from '@/features/dashboard/routes'
import DashboardPage from '@/features/dashboard/DashboardPage'
import StudentInspectionDashboardCard from '@/features/student-inspections/DashboardInspection'
import { studentInspectionRoutes } from '@/features/student-inspections/routes'
import { residenceRoutes } from '@/features/residences/routes'
import { inspectionRoutes } from '@/features/inspections/routes'
import { standardsRoutes } from '@/features/standards/routes'
import { checklistRoutes } from '@/features/checklist/routes'
import { moreRoutes } from '@/features/more/routes'

function DashboardWithStudentInspection() {
  return createElement(DashboardPage, null, createElement(StudentInspectionDashboardCard))
}

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
          ...dashboardRoutes.map((route) => route.index
            ? { ...route, Component: DashboardWithStudentInspection }
            : route),
          ...studentInspectionRoutes,
          ...residenceRoutes,
          ...standardsRoutes,
          ...inspectionRoutes,
          ...checklistRoutes,
          ...moreRoutes,
          // Platzhalter, bis das Putzplan-Feature (US-06/US-07) seine Route registriert.
          { path: 'putzplan', Component: ComingSoonPage },
          { path: 'forbidden', Component: ForbiddenPage },
        ],
      },
      { path: '*', Component: NotFoundPage },
    ],
  }]
}
