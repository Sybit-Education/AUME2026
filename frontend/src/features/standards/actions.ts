import { redirect, type ActionFunctionArgs } from 'react-router'
import { SessionExpired } from '@/lib/auth/response'
import { loginDestination } from '@/lib/auth/returnTo'
import { ensureSession, forgetUser } from '@/lib/auth/session'
import { errorKey, UiError, type ErrorKey } from '@/lib/errors'
import { saveInfoPages } from './api'

export type InfoPagesActionResult = { saved: true } | { error: ErrorKey }

// Eine Action schützt der Leseloader nicht: Sitzung und Privileg werden hier selbst geprüft.
export async function saveInfoPagesAction({ request }: ActionFunctionArgs): Promise<InfoPagesActionResult> {
  const toLogin = () => redirect(loginDestination(new URL(request.url).pathname))
  const { user } = await ensureSession()
  if (!user) throw toLogin()
  if (!user.privileges.includes('INFO_PAGE_EDIT')) throw redirect('/forbidden')

  const form = await request.formData()
  const text = (name: string) => String(form.get(name) ?? '')
  try {
    await saveInfoPages({
      process: text('process'),
      consequences: text('consequences'),
      standards: text('standards'),
    }, request.signal)
    return { saved: true }
  } catch (cause) {
    if (cause instanceof Response) throw cause
    if (cause instanceof SessionExpired) throw toLogin()
    if (cause instanceof UiError && cause.key === 'errors.sessionExpired') {
      forgetUser()
      throw toLogin()
    }
    return { error: errorKey(cause, 'errors.infoPagesSave') }
  }
}
