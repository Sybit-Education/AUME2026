import { redirect, type RouteObject } from 'react-router'
import { protectedLoader } from '@/lib/auth/loaders'

/** Geschützter Einstieg für ältere Lesezeichen zu US-23. */
export const inspectionProcessRoutes: RouteObject[] = [
  {
    path: 'process',
    loader: protectedLoader(undefined, async (_args, user) => {
      if (!user.roles.includes('STUDENT') || !user.privileges.includes('INFO_PAGE_READ')) {
        throw redirect('/forbidden')
      }
      throw redirect('/student/inspection/appointment#inspection-process')
    }),
  },
]
