import type de from '@/locales/de.json'

type MoreKey = `more.${keyof typeof de.more}`

export interface MoreItem {
  to: string
  icon: string
  label: MoreKey
  hint: MoreKey
  privilege?: string
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
    title: 'more.account',
    items: [
      { to: '/student/settings', icon: '⚙️', label: 'more.settings', hint: 'more.settingsHint' },
    ],
  },
]
