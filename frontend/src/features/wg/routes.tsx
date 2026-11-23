import type { RouteObject } from 'react-router'
import { protectedLoader } from '@/lib/auth/loaders'
import RequirePrivilege from '@/lib/auth/RequirePrivilege'
import WgPage from './WgPage'
import { wgAction } from './actions'
import { getMembership, type Membership } from './api'

export interface WgData {
  membership: Membership | null
}

export const wgRoutes: RouteObject[] = [{
  path: 'wg',
  element: <RequirePrivilege privilege="WG_MEMBERSHIP"><WgPage /></RequirePrivilege>,
  loader: protectedLoader('WG_MEMBERSHIP', async ({ request }): Promise<WgData> => ({
    membership: await getMembership(request.signal),
  })),
  action: wgAction,
}]

export const wgNavigation = {
  to: '/wg', label: 'navigation.wg', privilege: 'WG_MEMBERSHIP',
} as const
