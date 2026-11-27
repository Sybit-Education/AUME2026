import { redirect, type RouteObject } from 'react-router'
import { protectedLoader } from '@/lib/auth/loaders'
import RequirePrivilege from '@/lib/auth/RequirePrivilege'
import InspectionPlanningPage from './InspectionPlanningPage'
import { inspectionPlanningAction } from './actions'
import { getAnnouncementDraft, getPlanningOptions, listPeriods, type PlanningData } from './api'

const PRIVILEGE = 'INSPECTION_PLAN'

function periodParam(params: URLSearchParams, name: string): number | null {
  const value = params.get(name)
  return value !== null && /^\d+$/.test(value) ? Number(value) : null
}

export const inspectionPlanningRoutes: RouteObject[] = [{
  path: 'inspection-planning',
  element: <RequirePrivilege privilege={PRIVILEGE}><InspectionPlanningPage /></RequirePrivilege>,
  loader: protectedLoader(PRIVILEGE, async ({ request }): Promise<PlanningData> => {
    const params = new URL(request.url).searchParams
    const page = Number(params.get('page') ?? '0')
    if (!Number.isSafeInteger(page) || page < 0) throw redirect('/inspection-planning')
    const announce = periodParam(params, 'announce')
    const [options, periods, draft] = await Promise.all([
      getPlanningOptions(request.signal),
      listPeriods(page, request.signal),
      announce === null ? undefined : getAnnouncementDraft(announce, request.signal),
    ])
    return { options, periods, draft }
  }),
  action: inspectionPlanningAction,
}]

export const inspectionPlanningNavigation = {
  to: '/inspection-planning', label: 'navigation.inspectionPlanning', privilege: PRIVILEGE,
} as const
