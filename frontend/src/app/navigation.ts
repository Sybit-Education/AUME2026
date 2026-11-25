import type de from '@/locales/de.json'
import type { NavIconName } from '@/components/NavIcon'
import { residenceNavigation } from '@/features/residences/routes'
import { inspectionPlanningNavigation } from '@/features/inspection-planning/routes'
import { inspectionNavigation } from '@/features/inspections/routes'
import { standardsNavigation } from '@/features/standards/routes'

type NavigationItem = {
  to: string
  label: `navigation.${keyof typeof de.navigation}`
  privilege?: string
}

export const navigation: NavigationItem[] = [
  { to: '/', label: 'navigation.dashboard' },
  residenceNavigation,
  inspectionPlanningNavigation,
  inspectionNavigation,
  standardsNavigation,
]

type StudentTab = NavigationItem & { icon: NavIconName; activeWithin?: string }

/**
 * Mobile Hauptnavigation der Studierenden (Wireframe: Start, Putzplan, Kontrolle, Mehr).
 * Weitere Studierenden-Funktionen kommen unter „Mehr“ dazu, nicht als fünfter Tab.
 */
export const studentTabs: StudentTab[] = [
  { to: '/', label: 'navigation.start', icon: 'home' },
  { to: '/putzplan', label: 'navigation.cleaningPlan', icon: 'plan' },
  { to: '/kontrolle/termin', label: 'navigation.inspection', icon: 'inspection', activeWithin: '/kontrolle/*' },
  { to: '/mehr', label: 'navigation.more', icon: 'more' },
]

const staffRoles = ['CARETAKER', 'CLERK']

/** Studierende ohne weitere Rolle sehen die App-Ansicht mit Tab-Leiste statt der Kopfnavigation. */
export function usesStudentView(roles: string[]): boolean {
  return roles.includes('STUDENT') && !roles.some((role) => staffRoles.includes(role))
}
