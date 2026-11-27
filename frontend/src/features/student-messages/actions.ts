import { redirect, type ActionFunctionArgs } from 'react-router'
import { errorKey, UiError, type ErrorKey } from '@/lib/errors'
import { SessionExpired } from '@/lib/auth/response'
import { loginDestination } from '@/lib/auth/returnTo'
import { ensureSession, forgetUser } from '@/lib/auth/session'
import { markMessageRead } from './api'
import { requestUnreadCountRefresh } from './unreadCountRefresh'

export type MessageReadResult = { read: true } | { error: ErrorKey }

export async function messageReadAction({ request }: ActionFunctionArgs): Promise<MessageReadResult> {
  const { pathname, search } = new URL(request.url)
  const login = () => redirect(loginDestination(pathname + search))
  const { user } = await ensureSession()
  if (!user) throw login()
  if (!user.roles.includes('STUDENT')) throw redirect('/forbidden')

  const form = await request.formData()
  const id = Number(form.get('id'))
  if (!Number.isSafeInteger(id) || id < 1) return { error: 'errors.studentMessageRead' }

  try {
    await markMessageRead(id, request.signal)
    requestUnreadCountRefresh(user)
    return { read: true }
  } catch (cause) {
    if (request.signal.aborted || cause instanceof Response) throw cause
    if (cause instanceof SessionExpired) throw login()
    if (cause instanceof UiError && cause.key === 'errors.sessionExpired') {
      forgetUser()
      throw login()
    }
    return { error: errorKey(cause, 'errors.studentMessageRead') }
  }
}
