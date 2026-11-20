import { redirect, type ActionFunctionArgs } from 'react-router'
import { loginDestination } from '@/lib/auth/returnTo'
import { SessionExpired } from '@/lib/auth/response'
import { ensureSession, forgetUser } from '@/lib/auth/session'
import { errorKey, UiError, type ErrorKey } from '@/lib/errors'
import { assignStaff } from './api'

export interface StaffActionResult {
  error: ErrorKey
}

function id(value: FormDataEntryValue | null): number | null {
  return typeof value === 'string' && /^\d+$/.test(value) ? Number(value) : null
}

// Eine Action schützt der Leseloader nicht: Sitzung und Privileg werden hier selbst geprüft.
export async function assignStaffAction({ request }: ActionFunctionArgs): Promise<StaffActionResult | Response> {
  const url = new URL(request.url)
  const toLogin = () => redirect(loginDestination(url.pathname + url.search))

  const { user } = await ensureSession()
  if (!user) throw toLogin()
  if (!user.privileges.includes('RESIDENCE_ASSIGN_STAFF')) throw redirect('/forbidden')

  const form = await request.formData()
  const residenceId = id(form.get('residenceId'))
  const caretakerId = id(form.get('caretakerId'))
  const deputyValue = form.get('deputyId')
  const deputyId = deputyValue === '' || deputyValue === null ? null : id(deputyValue)
  if (residenceId === null) return { error: 'errors.assignStaff' }
  if (caretakerId === null) return { error: 'errors.caretakerRequired' }
  if (deputyValue !== '' && deputyValue !== null && deputyId === null) return { error: 'errors.unknownCaretaker' }

  try {
    await assignStaff(residenceId, caretakerId, deputyId, request.signal)
  } catch (cause) {
    if (cause instanceof Response) throw cause // 403: requireOk leitet auf /forbidden um
    if (cause instanceof SessionExpired) throw toLogin()
    if (cause instanceof UiError && cause.key === 'errors.sessionExpired') {
      forgetUser()
      throw toLogin()
    }
    return { error: errorKey(cause, 'errors.assignStaff') }
  }

  const page = url.searchParams.get('page')
  return redirect(page !== null && /^\d+$/.test(page) ? `/residences?page=${page}` : '/residences')
}
