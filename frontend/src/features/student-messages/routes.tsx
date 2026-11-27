import { redirect, type RouteObject } from 'react-router'
import { protectedLoader } from '@/lib/auth/loaders'
import { pageHandle } from '@/lib/pageHandle'
import StudentMessagesPage from './StudentMessagesPage'
import { messageReadAction } from './actions'
import { getMessages } from './api'

export const studentMessagesRoutes: RouteObject[] = [{
  path: 'messages',
  Component: StudentMessagesPage,
  handle: pageHandle({ title: 'more.messages', back: '/student/more' }),
  loader: protectedLoader(undefined, ({ request }, user) => {
    if (!user.roles.includes('STUDENT')) throw redirect('/forbidden')
    const requestedPage = Number(new URL(request.url).searchParams.get('page'))
    const page = Number.isSafeInteger(requestedPage) && requestedPage >= 0 && requestedPage * 20 <= 2147483647
      ? requestedPage : 0
    return getMessages(page, request.signal)
  }),
  action: messageReadAction,
}]
