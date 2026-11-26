import type { RouteObject } from 'react-router'
import { pageHandle } from '@/lib/pageHandle'
import MorePage from './MorePage'
import SettingsPage from './SettingsPage'
import './more.css'

export const moreRoutes: RouteObject[] = [
  { path: 'more', Component: MorePage, handle: pageHandle({ title: 'more.title' }) },
  { path: 'settings', Component: SettingsPage, handle: pageHandle({ title: 'settings.title', back: '/student/more' }) },
]
