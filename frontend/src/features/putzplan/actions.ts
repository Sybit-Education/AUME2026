import { redirect, type ActionFunctionArgs } from 'react-router'
import { SessionExpired } from '@/lib/auth/response'
import { loginDestination } from '@/lib/auth/returnTo'
import { ensureSession, forgetUser } from '@/lib/auth/session'
import { errorKey, UiError, type ErrorKey } from '@/lib/errors'
import { deleteTask, rhythms, saveTask, setDone, type Rhythm } from './api'

export interface PlanActionResult {
  error: ErrorKey
}

function id(value: FormDataEntryValue | null): number | null {
  return typeof value === 'string' && /^\d+$/.test(value) ? Number(value) : null
}

// Actions are not protected by the loader, so check session and privilege here.
export async function planAction({ request }: ActionFunctionArgs): Promise<PlanActionResult | Response> {
  const url = new URL(request.url)
  const toLogin = () => redirect(loginDestination(url.pathname + url.search))
  const { user } = await ensureSession()
  if (!user) throw toLogin()
  if (!user.privileges.includes('WG_MEMBERSHIP')) throw redirect('/forbidden')

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
    if (cause instanceof Response) throw cause // 403: requireOk redirects to /forbidden
    if (cause instanceof SessionExpired) throw toLogin()
    if (cause instanceof UiError && cause.key === 'errors.sessionExpired') {
      forgetUser()
      throw toLogin()
    }
    return { error: errorKey(cause, 'errors.planSave') }
  }

  // Close the form and stay in the current week.
  const week = url.searchParams.get('week')
  return redirect(week && /^\d{4}-\d{2}-\d{2}$/.test(week) ? `/putzplan?week=${week}` : '/putzplan')
}
