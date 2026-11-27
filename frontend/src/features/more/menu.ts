import type de from '@/locales/de.json'
import type { TileIconName } from '@/components/TileIcon'

type MoreKey = `more.${keyof typeof de.more}`

export interface MoreItem {
  to: string
  icon: TileIconName
  label: MoreKey
  hint: MoreKey
  privilege?: string
  role?: 'STUDENT'
}

export interface MoreGroup {
  title: MoreKey
  items: MoreItem[]
}

/**
 * Einträge unter „Mehr“. Nur freigegebene Funktionen eintragen; weitere Gruppen aus dem Wireframe
 * (Wohnen, Campus) kommen dazu, sobald ihre Features umgesetzt sind. Einträge mit Privileg sind nur
 * für berechtigte Nutzer sichtbar.
 */
export const moreGroups: MoreGroup[] = [
  {
    title: 'more.living',
    items: [
      { to: '/student/flat', icon: 'flat', label: 'more.wg', hint: 'more.wgHint', privilege: 'WG_MEMBERSHIP' },
    ],
  },
  {
    title: 'more.communication',
    items: [
      { to: '/student/messages', icon: 'list', label: 'more.messages', hint: 'more.messagesHint', role: 'STUDENT' },
    ],
  },
  {
    title: 'more.account',
    items: [
      { to: '/student/settings', icon: 'settings', label: 'more.settings', hint: 'more.settingsHint' },
    ],
  },
]
