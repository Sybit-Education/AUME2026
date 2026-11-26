import { redirect, type ActionFunctionArgs } from 'react-router'
import { SessionExpired } from '@/lib/auth/response'
import { loginDestination } from '@/lib/auth/returnTo'
import { ensureSession, forgetUser } from '@/lib/auth/session'
import { errorKey, UiError, type ErrorKey } from '@/lib/errors'
import { deleteTask, rhythms, saveTask, setDone, type Rhythm } from './api'
import { planPath } from './paths'

export interface PlanActionResult {
  error: ErrorKey
}

function id(value: FormDataEntryValue | null): number | null {
  return typeof value === 'string' && /^\d+$/.test(value) ? Number(value) : null
}

// Actions are not protected by the loader, so check session and privilege here.
async function requirePlanUser(returnTo: string) {
  const toLogin = () => redirect(loginDestination(returnTo))
  const { user } = await ensureSession()
  if (!user) throw toLogin()
  if (!user.privileges.includes('WG_MEMBERSHIP')) throw redirect('/forbidden')
  return toLogin
}

function failure(cause: unknown, toLogin: () => Response): PlanActionResult {
  if (cause instanceof Response) throw cause // 403: requireOk redirects to /forbidden
  if (cause instanceof SessionExpired) throw toLogin()
  if (cause instanceof UiError && cause.key === 'errors.sessionExpired') {
    forgetUser()
    throw toLogin()
  }
  return { error: errorKey(cause, 'errors.planSave') }
}

export async function planAction({ request }: ActionFunctionArgs): Promise<PlanActionResult | Response> {
  const url = new URL(request.url)
  const toLogin = await requirePlanUser(url.pathname + url.search)

  const form = await request.formData()
  const intent = form.get('intent')
  const taskId = id(form.get('id'))

  try {
    if (intent === 'save') {
      const title = String(form.get('title') ?? '').trim()
      const assigneeId = id(form.get('assigneeId'))
      const rhythm = String(form.get('rhythm')) as Rhythm
      const startWeek = String(form.get('startWeek') ?? '')
      if (!title) return { error: 'errors.planTitle' }
      if (assigneeId === null) return { error: 'errors.planAssignee' }
      if (!rhythms.includes(rhythm) || !/^\d{4}-\d{2}-\d{2}$/.test(startWeek)) return { error: 'errors.planSave' }
      const criterionId = id(form.get('criterionId'))
      await saveTask(taskId, {
        title, assigneeId, rhythm, startWeek, ...(criterionId !== null && { criterionId }),
      }, request.signal)
    } else if (taskId === null) {
      return { error: 'errors.planSave' }
    } else if (intent === 'delete') {
      await deleteTask(taskId, request.signal)
    } else if (intent === 'toggle') {
      await setDone(taskId, form.get('done') === 'true', request.signal)
    } else {
      return { error: 'errors.planSave' }
    }
  } catch (cause) {
    return failure(cause, toLogin)
  }

  // Close the form and stay in the current week.
  const week = url.searchParams.get('week')
  return redirect(week && /^\d{4}-\d{2}-\d{2}$/.test(week) ? `${planPath}?week=${week}` : planPath)
}

/**
 * Abhaken außerhalb des Putzplans, etwa auf der Startseite. Ohne Weiterleitung, damit die Seite
 * stehen bleibt und nur ihre Daten neu lädt.
 */
export async function toggleTaskAction({ request }: ActionFunctionArgs): Promise<PlanActionResult | null> {
  const toLogin = await requirePlanUser(new URL(request.url).pathname)
  const form = await request.formData()
  const taskId = id(form.get('id'))
  if (form.get('intent') !== 'toggle' || taskId === null) return { error: 'errors.planSave' }

  try {
    await setDone(taskId, form.get('done') === 'true', request.signal)
  } catch (cause) {
    return failure(cause, toLogin)
  }
  return null
}
