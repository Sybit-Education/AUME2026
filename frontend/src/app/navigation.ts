import type de from '../locales/de.json'
import { residenceNavigation } from '../features/residences/routes'

type NavigationItem = {
  to: string
  label: `navigation.${keyof typeof de.navigation}`
  privilege?: string
}

export const navigation: NavigationItem[] = [
  { to: '/', label: 'navigation.dashboard' },
  residenceNavigation,
]
