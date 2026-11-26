import type { RouteObject } from 'react-router'
import { protectedLoader } from '@/lib/auth/loaders'
import { loadProfile } from './api'
import ProfilePage from './ProfilePage'

export const profileRoutes: RouteObject[] = [{
  path: 'profile',
  Component: ProfilePage,
  loader: protectedLoader(undefined, ({ request }, user) => loadProfile(user, request.signal)),
}]

export const profileNavigation = {
  to: '/profile',
  label: 'navigation.profile',
} as const
