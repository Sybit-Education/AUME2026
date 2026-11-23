import { redirect, type ActionFunctionArgs } from 'react-router'
import { SessionExpired } from '@/lib/auth/response'
import { loginDestination } from '@/lib/auth/returnTo'
import { ensureSession, forgetUser } from '@/lib/auth/session'
import { errorKey, UiError, type ErrorKey } from '@/lib/errors'
import { joinWg, leaveWg } from './api'

export interface WgActionResult {
  error: ErrorKey
}

// Actions are not protected by the loader, so check session and privilege here.
export async function wgAction({ request }: ActionFunctionArgs): Promise<WgActionResult | Response> {
  const toLogin = () => redirect(loginDestination(new URL(request.url).pathname))
  const { user } = await ensureSession()
  if (!user) throw toLogin()
  if (!user.privileges.includes('WG_MEMBERSHIP')) throw redirect('/forbidden')

  const form = await request.formData()
  const leaving = form.get('intent') === 'leave'
  const code = String(form.get('code') ?? '').trim()
  if (!leaving && !code) return { error: 'errors.wgInvalidCode' }

  try {
    if (leaving) await leaveWg(request.signal)
    else await joinWg(code, request.signal)
  } catch (cause) {
    if (cause instanceof Response) throw cause // 403: requireOk redirects to /forbidden
    if (cause instanceof SessionExpired) throw toLogin()
    if (cause instanceof UiError && cause.key === 'errors.sessionExpired') {
      forgetUser()
      throw toLogin()
    }
    return { error: errorKey(cause, leaving ? 'errors.wgLeave' : 'errors.wgJoin') }
  }
  return redirect('/wg')
}
