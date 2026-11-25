import { redirect, type RouteObject } from 'react-router'
import { protectedLoader } from '@/lib/auth/loaders'
import RequirePrivilege from '@/lib/auth/RequirePrivilege'
import InspectionPlanningPage from './InspectionPlanningPage'
import { inspectionPlanningAction } from './actions'
import { getPlanningOptions, listPeriods, type PlanningData } from './api'

const PRIVILEGE = 'INSPECTION_PLAN'

export const inspectionPlanningRoutes: RouteObject[] = [{
  path: 'inspection-planning',
  element: <RequirePrivilege privilege={PRIVILEGE}><InspectionPlanningPage /></RequirePrivilege>,
  loader: protectedLoader(PRIVILEGE, async ({ request }): Promise<PlanningData> => {
    const page = Number(new URL(request.url).searchParams.get('page') ?? '0')
    if (!Number.isSafeInteger(page) || page < 0) throw redirect('/inspection-planning')
    const [options, periods] = await Promise.all([getPlanningOptions(request.signal), listPeriods(page, request.signal)])
    return { options, periods }
  }),
  action: inspectionPlanningAction,
}]

export const inspectionPlanningNavigation = {
  to: '/inspection-planning', label: 'navigation.inspectionPlanning', privilege: PRIVILEGE,
} as const
