import { redirect, type ActionFunctionArgs } from 'react-router'
import { SessionExpired } from '@/lib/auth/response'
import { loginDestination } from '@/lib/auth/returnTo'
import { ensureSession, forgetUser } from '@/lib/auth/session'
import { errorKey, UiError, type ErrorKey } from '@/lib/errors'
import { agreeToRequest, counterRequest, type CounterInput } from './requestsApi'

export const RESPOND_PRIVILEGE = 'INSPECTION_PERIOD_RESPOND'

export type RequestIntent = 'agree' | 'counter'

export interface RequestActionResult {
  intent: RequestIntent
  error: ErrorKey
}

const saved: Record<RequestIntent, string> = { agree: 'agreed', counter: 'countered' }

function number(value: FormDataEntryValue | null): number | null {
  return typeof value === 'string' && /^\d+$/.test(value) ? Number(value) : null
}

function text(value: FormDataEntryValue | null): string | null {
  return typeof value === 'string' && value.trim() !== '' ? value : null
}

function counterInput(form: FormData): CounterInput {
  return {
    startsOn: text(form.get('startsOn')),
    endsOn: text(form.get('endsOn')),
    windowStart: text(form.get('windowStart')),
    windowEnd: text(form.get('windowEnd')),
    reason: text(form.get('reason'))?.trim() ?? null,
  }
}

// Eine Action schützt der Leseloader nicht: Sitzung und Privileg werden hier selbst geprüft.
export async function inspectionRequestsAction({ request }: ActionFunctionArgs): Promise<RequestActionResult | Response> {
  const url = new URL(request.url)
  const toLogin = () => redirect(loginDestination(url.pathname + url.search))
  const { user } = await ensureSession()
  if (!user) throw toLogin()
  if (!user.privileges.includes(RESPOND_PRIVILEGE)) throw redirect('/forbidden')

  const form = await request.formData()
  const intent: RequestIntent = form.get('intent') === 'counter' ? 'counter' : 'agree'
  const periodId = number(form.get('periodId'))
  const version = number(form.get('version'))
  if (periodId === null || version === null) return { intent, error: 'errors.inspectionRequestSave' }

  try {
    if (intent === 'agree') await agreeToRequest(periodId, version, request.signal)
    else {
      const input = counterInput(form)
      // Die Begründung ist Pflicht. Ohne sie muss niemand auf eine Antwort des Backends warten.
      if (!input.reason) return { intent, error: 'errors.requestReasonRequired' }
      await counterRequest(periodId, input, version, request.signal)
    }
  } catch (cause) {
    if (cause instanceof Response) throw cause
    if (cause instanceof SessionExpired) throw toLogin()
    if (cause instanceof UiError && cause.key === 'errors.sessionExpired') {
      forgetUser()
      throw toLogin()
    }
    return { intent, error: errorKey(cause, 'errors.inspectionRequestSave') }
  }

  const next = new URLSearchParams(url.search)
  next.delete('answer')
  next.delete('counter')
  next.set('saved', saved[intent])
  return redirect(`${url.pathname}?${next}`)
}
