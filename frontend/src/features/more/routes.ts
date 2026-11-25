import type { RouteObject } from 'react-router'
import MorePage from './MorePage'
import SettingsPage from './SettingsPage'
import './more.css'

export const moreRoutes: RouteObject[] = [
  { path: 'more', Component: MorePage },
  { path: 'settings', Component: SettingsPage },
]
