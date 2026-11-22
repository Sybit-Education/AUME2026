import type de from '@/locales/de.json'
import { residenceNavigation } from '@/features/residences/routes'
import { standardsNavigation } from '@/features/standards/routes'

type NavigationItem = {
  to: string
  label: `navigation.${keyof typeof de.navigation}`
  privilege?: string
}

export const navigation: NavigationItem[] = [
  { to: '/', label: 'navigation.dashboard' },
  residenceNavigation,
  standardsNavigation,
]

type StudentTab = NavigationItem & { icon: string }

/**
 * Mobile Hauptnavigation der Studierenden (Wireframe: Start, Putzplan, Kontrolle, Mehr).
 * Weitere Studierenden-Funktionen kommen unter „Mehr“ dazu, nicht als fünfter Tab.
 */
export const studentTabs: StudentTab[] = [
  { to: '/', label: 'navigation.start', icon: '🏠' },
  { to: '/putzplan', label: 'navigation.cleaningPlan', icon: '🧽' },
  { to: '/kontrolle', label: 'navigation.inspection', icon: '📋' },
  { to: '/mehr', label: 'navigation.more', icon: '☰' },
]

const staffRoles = ['CARETAKER', 'CLERK']

/** Studierende ohne weitere Rolle sehen die App-Ansicht mit Tab-Leiste statt der Kopfnavigation. */
export function usesStudentView(roles: string[]): boolean {
  return roles.includes('STUDENT') && !roles.some((role) => staffRoles.includes(role))
}
