import type { RouteObject } from 'react-router'
import { protectedLoader } from '@/lib/auth/loaders'
import { loadDashboard } from '@/features/dashboard/api'
import { loadOverview } from '@/features/inspection-overview/api'
import { INSPECTION_OVERVIEW_PRIVILEGE } from '@/features/inspection-overview/routes'
import { CARETAKER_OVERVIEW_PRIVILEGE, loadOverview as loadCaretakerOverview } from '@/features/overview/routes'
import HomePage from './HomePage'

// Die Daten müssen zur Seite passen, die HomePage zeigt. Beide entscheiden über dieselben Privilegien.
export const homeRoutes: RouteObject[] = [{
  index: true,
  Component: HomePage,
  loader: protectedLoader<unknown>(undefined, (args, user) => {
    if (user.privileges.includes(INSPECTION_OVERVIEW_PRIVILEGE)) return loadOverview(args.request)
    if (user.privileges.includes(CARETAKER_OVERVIEW_PRIVILEGE)) return loadCaretakerOverview(args)
    return loadDashboard(user, args.request.signal)
  }),
}]
