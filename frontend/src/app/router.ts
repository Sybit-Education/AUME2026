import { createElement } from 'react'
import { Navigate, type RouteObject } from 'react-router'
import AppLayout from './AppLayout'
import RequireAuth from './RequireAuth'
import RouteErrorLayout from './RouteErrorLayout'
import ForbiddenPage from './routes/ForbiddenPage'
import NotFoundPage from './routes/NotFoundPage'
import ComingSoonPage from './routes/ComingSoonPage'
import StudentControlLayout from './routes/StudentControlLayout'
import LoadingPage from '@/components/LoadingPage'
import { ensureSession } from '@/lib/auth/session'
import { authRoutes } from '@/features/auth/routes'
import { dashboardRoutes } from '@/features/dashboard/routes'
import DashboardPage from '@/features/dashboard/DashboardPage'
import StudentInspectionDashboardCard from '@/features/student-inspections/DashboardInspection'
import { studentInspectionRoutes } from '@/features/student-inspections/routes'
import { residenceRoutes } from '@/features/residences/routes'
import { inspectionPlanningRoutes } from '@/features/inspection-planning/routes'
import { inspectionRoutes } from '@/features/inspections/routes'
import { standardsRoutes } from '@/features/standards/routes'
import { checklistRoutes } from '@/features/checklist/routes'
import { inspectionProcessRoutes } from '@/features/inspection-process/routes'
import { moreRoutes } from '@/features/more/routes'

/** Alte deutsche Adressen (vor dem Präfix „student/“) leiten weiter, damit Lesezeichen funktionieren. */
const legacyStudentRedirects: RouteObject[] = Object.entries({
  kontrolle: '/student/inspection/appointment',
  'kontrolle/termin': '/student/inspection/appointment',
  'kontrolle/checkliste': '/student/inspection/checklist',
  'kontrolle/ergebnis': '/student/inspection/result',
  'kontrolle/verlauf': '/student/inspection/history',
  putzplan: '/student/cleaning-plan',
  mehr: '/student/more',
  'mehr/einstellungen': '/student/settings',
}).map(([path, to]) => ({ path, element: createElement(Navigate, { to, replace: true }) }))

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
          {
            // Studierenden-Adressen mit Präfix „student/“, damit sie nicht mit den
            // „inspections“-Seiten der Objektbetreuung kollidieren.
            path: 'student',
            children: [
              { index: true, element: createElement(Navigate, { to: '/', replace: true }) },
              {
                path: 'inspection',
                Component: StudentControlLayout,
                children: [
                  { index: true, element: createElement(Navigate, { to: 'appointment', replace: true }) },
                  ...studentInspectionRoutes,
                  ...checklistRoutes,
                  ...inspectionProcessRoutes,
                  { path: 'result', Component: ComingSoonPage },
                  { path: 'history', Component: ComingSoonPage },
                ],
              },
              // Platzhalter, bis das Putzplan-Feature (US-06/US-07) seine Route registriert.
              { path: 'cleaning-plan', Component: ComingSoonPage },
              ...moreRoutes,
            ],
          },
          ...residenceRoutes,
          ...standardsRoutes,
          ...inspectionRoutes,
          ...inspectionPlanningRoutes,
          { path: 'forbidden', Component: ForbiddenPage },
        ],
      },
      ...legacyStudentRedirects,
      { path: '*', Component: NotFoundPage },
    ],
  }]
}
