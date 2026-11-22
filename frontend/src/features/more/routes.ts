import type { RouteObject } from 'react-router'
import MorePage from './MorePage'
import SettingsPage from './SettingsPage'
import './more.css'

export const moreRoutes: RouteObject[] = [
  { path: 'mehr', Component: MorePage },
  { path: 'mehr/einstellungen', Component: SettingsPage },
]
