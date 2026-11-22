import { redirect, type ActionFunctionArgs } from 'react-router'
import { SessionExpired } from '@/lib/auth/response'
import { loginDestination } from '@/lib/auth/returnTo'
import { ensureSession, forgetUser } from '@/lib/auth/session'
import { errorKey, UiError, type ErrorKey } from '@/lib/errors'
import { acceptCounter, createPeriod, updatePeriod, type PeriodInput } from './api'

export type PlanningIntent = 'create' | 'update' | 'accept'

export interface PlanningActionResult {
  intent: PlanningIntent
  error: ErrorKey
}

const saved: Record<PlanningIntent, string> = { create: 'created', update: 'updated', accept: 'accepted' }

function number(value: FormDataEntryValue | null): number | null {
  return typeof value === 'string' && /^\d+$/.test(value) ? Number(value) : null
}

function text(value: FormDataEntryValue | null): string | null {
  return typeof value === 'string' && value !== '' ? value : null
}

function periodInput(form: FormData): PeriodInput {
  return {
    startsOn: text(form.get('startsOn')),
    endsOn: text(form.get('endsOn')),
    windowStart: text(form.get('windowStart')),
    windowEnd: text(form.get('windowEnd')),
    caretakerId: number(form.get('caretakerId')),
  }
}

// Eine Action schützt der Leseloader nicht: Sitzung und Privileg werden hier selbst geprüft.
export async function inspectionPlanningAction({ request }: ActionFunctionArgs): Promise<PlanningActionResult | Response> {
  const url = new URL(request.url)
  const toLogin = () => redirect(loginDestination(url.pathname + url.search))
  const { user } = await ensureSession()
  if (!user) throw toLogin()
  if (!user.privileges.includes('INSPECTION_PLAN')) throw redirect('/forbidden')

  const form = await request.formData()
  const value = form.get('intent')
  const intent: PlanningIntent = value === 'update' || value === 'accept' ? value : 'create'
  const residenceId = number(form.get('residenceId'))
  const periodId = number(form.get('periodId'))
  const version = number(form.get('version'))

  try {
    if (intent === 'create') {
      if (residenceId === null) return { intent, error: 'errors.inspectionPlanningSave' }
      await createPeriod(residenceId, periodInput(form), request.signal)
    } else {
      if (periodId === null || version === null) return { intent, error: 'errors.inspectionPlanningSave' }
      if (intent === 'update') await updatePeriod(periodId, periodInput(form), version, request.signal)
      else await acceptCounter(periodId, version, request.signal)
    }
  } catch (cause) {
    if (cause instanceof Response) throw cause
    if (cause instanceof SessionExpired) throw toLogin()
    if (cause instanceof UiError && cause.key === 'errors.sessionExpired') {
      forgetUser()
      throw toLogin()
    }
    return { intent, error: errorKey(cause, 'errors.inspectionPlanningSave') }
  }

  const next = new URLSearchParams(url.search)
  next.delete('edit')
  next.set('saved', saved[intent])
  return redirect(`${url.pathname}?${next}`)
}
