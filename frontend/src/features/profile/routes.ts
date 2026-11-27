import { redirect, type RouteObject } from 'react-router'
import { protectedLoader } from '@/lib/auth/loaders'
import { loadProfile } from './api'
import ProfilePage from './ProfilePage'

/** Das Profil zeigt die Zuständigkeiten der Objektbetreuung; die Sachbearbeitung hat keine Profilseite. */
const privilege = 'INSPECTION_PERFORM'

export const profileRoutes: RouteObject[] = [{
  path: 'profile',
  Component: ProfilePage,
  loader: protectedLoader(undefined, ({ request }, user) => {
    if (!user.privileges.includes(privilege)) throw redirect('/forbidden')
    return loadProfile(user, request.signal)
  }),
}]

export const profileNavigation = {
  to: '/profile',
  label: 'navigation.profile',
  privilege,
} as const
