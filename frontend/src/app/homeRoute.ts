import type { RouteObject } from 'react-router'
import { protectedLoader } from '@/lib/auth/loaders'
import { loadHome } from './studentHome'
import { loadOverview } from '@/features/inspection-overview/api'
import { INSPECTION_OVERVIEW_PRIVILEGE } from '@/features/inspection-overview/routes'
import { CARETAKER_OVERVIEW_PRIVILEGE, loadCurrentInspections } from '@/features/inspections/currentInspections'
import { toggleTaskAction } from '@/features/putzplan/actions'
import { pageHandle } from '@/lib/pageHandle'
import HomePage from './HomePage'

// Die Daten müssen zur Seite passen, die HomePage zeigt. Beide entscheiden über dieselben Privilegien.
export const homeRoutes: RouteObject[] = [{
  index: true,
  Component: HomePage,
  handle: pageHandle({ title: 'home.hello' }),
  loader: protectedLoader<unknown>(undefined, (args, user) => {
    if (user.privileges.includes(INSPECTION_OVERVIEW_PRIVILEGE)) return loadOverview(args.request)
    if (user.privileges.includes(CARETAKER_OVERVIEW_PRIVILEGE)) return loadCurrentInspections(user, args.request.signal)
    return loadHome(user, args.request.signal)
  }),
  // Putzaufgaben auf der Startseite abhaken (#99).
  action: toggleTaskAction,
}]
