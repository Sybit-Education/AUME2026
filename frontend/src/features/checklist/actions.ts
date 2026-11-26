import { redirect, type ActionFunctionArgs } from 'react-router'
import { SessionExpired } from '@/lib/auth/response'
import { loginDestination } from '@/lib/auth/returnTo'
import { ensureSession, forgetUser } from '@/lib/auth/session'
import { errorKey, UiError, type ErrorKey } from '@/lib/errors'
import { resetSelfCheck, setSelfCheck } from './api'

export interface SelfCheckActionResult {
  error: ErrorKey
}

/** Selbstcheck abhaken oder zurücksetzen (US-84). Danach lädt die Seite ihre Daten neu. */
export async function selfCheckAction({ request }: ActionFunctionArgs): Promise<SelfCheckActionResult | null> {
  const toLogin = () => redirect(loginDestination(new URL(request.url).pathname))
  const { user } = await ensureSession()
  if (!user) throw toLogin()
  if (!user.privileges.includes('WG_MEMBERSHIP') || !user.privileges.includes('CHECKLIST_READ')) {
    throw redirect('/forbidden')
  }

  const form = await request.formData()
  const intent = form.get('intent')
  const criterion = form.get('criterion')
  try {
    if (intent === 'reset') {
      await resetSelfCheck(request.signal)
    } else if (intent === 'check' && typeof criterion === 'string' && /^\d+$/.test(criterion)) {
      await setSelfCheck(Number(criterion), form.get('done') === 'true', request.signal)
    } else {
      return { error: 'errors.selfCheckSave' }
    }
  } catch (cause) {
    if (cause instanceof Response) throw cause // 403: requireOk redirects to /forbidden
    if (cause instanceof SessionExpired) throw toLogin()
    if (cause instanceof UiError && cause.key === 'errors.sessionExpired') {
      forgetUser()
      throw toLogin()
    }
    return { error: errorKey(cause, 'errors.selfCheckSave') }
  }
  return null
}
