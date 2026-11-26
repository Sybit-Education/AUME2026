import { redirect, type RouteObject } from 'react-router'
import { protectedLoader } from '@/lib/auth/loaders'
import RequirePrivilege from '@/lib/auth/RequirePrivilege'
import PlanPage from './PlanPage'
import { planAction } from './actions'
import { getPlan, type Plan } from './api'
import { planPath } from './paths'
import { pageHandle } from '@/lib/pageHandle'

export interface PlanData {
  plan: Plan | null
}

export const planRoutes: RouteObject[] = [{
  path: 'cleaning-plan',
  handle: pageHandle({ title: 'navigation.cleaningPlan' }),
  element: <RequirePrivilege privilege="WG_MEMBERSHIP"><PlanPage /></RequirePrivilege>,
  loader: protectedLoader('WG_MEMBERSHIP', async ({ request }): Promise<PlanData> => {
    const week = new URL(request.url).searchParams.get('week')
    if (week !== null && (!/^\d{4}-\d{2}-\d{2}$/.test(week) || Number.isNaN(Date.parse(week)))) {
      throw redirect(planPath)
    }
    return { plan: await getPlan(week, request.signal) }
  }),
  action: planAction,
}]
