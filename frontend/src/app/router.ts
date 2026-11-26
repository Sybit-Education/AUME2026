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
import { homeRoutes } from './homeRoute'
import { studentInspectionRoutes } from '@/features/student-inspections/routes'
import { residenceRoutes } from '@/features/residences/routes'
import { profileRoutes } from '@/features/profile/routes'
import { inspectionPlanningRoutes } from '@/features/inspection-planning/routes'
import { inspectionOverviewRoutes } from '@/features/inspection-overview/routes'
import { inspectionRoutes } from '@/features/inspections/routes'
import { standardsRoutes } from '@/features/standards/routes'
import { checklistRoutes } from '@/features/checklist/routes'
import { inspectionProcessRoutes } from '@/features/inspection-process/routes'
import { moreRoutes } from '@/features/more/routes'
import { wgRoutes } from '@/features/wg/routes'
import { planRoutes } from '@/features/putzplan/routes'

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
                  { path: 'history', Component: ComingSoonPage },
                ],
              },
              ...planRoutes,
              ...wgRoutes,
              ...moreRoutes,
            ],
          },
          ...residenceRoutes,
          ...profileRoutes,
          ...standardsRoutes,
          ...inspectionRoutes,
          ...inspectionPlanningRoutes,
          ...inspectionOverviewRoutes,
          { path: 'forbidden', Component: ForbiddenPage },
        ],
      },
      ...legacyStudentRedirects,
      { path: '*', Component: NotFoundPage },
    ],
  }]
}
