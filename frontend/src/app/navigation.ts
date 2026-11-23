import type de from '@/locales/de.json'
import { residenceNavigation } from '@/features/residences/routes'
import { profileNavigation } from '@/features/profile/routes'
import { inspectionNavigation } from '@/features/inspections/routes'
import { standardsNavigation } from '@/features/standards/routes'

type NavigationItem = {
  to: string
  label: `navigation.${keyof typeof de.navigation}`
  privilege?: string
}

export const navigation: NavigationItem[] = [
  { to: '/', label: 'navigation.dashboard' },
  profileNavigation,
  residenceNavigation,
  inspectionNavigation,
  standardsNavigation,
]
