import { api } from '@/lib/auth/client'
import { requireOk } from '@/lib/auth/response'
import { UiError, type ErrorKey } from '@/lib/errors'

export type Rhythm = 'WEEKLY' | 'BIWEEKLY' | 'MONTHLY'
export const rhythms: Rhythm[] = ['WEEKLY', 'BIWEEKLY', 'MONTHLY']

export interface PlanTask {
  id: number
  title: string
  rhythm: Rhythm
  startWeek: string
  assigneeId: number | null
  assigneeName: string | null
  mine: boolean
  done: boolean
  doneBy: string | null
  checkable: boolean
}

// Unused checklist criterion in the current language.
export interface PlanSuggestion {
  criterionId: number
  area: string
  title: string
}

export interface Plan {
  week: string
  currentWeek: string
  me: number
  members: { id: number; name: string }[]
  tasks: PlanTask[]
  allTitles: string[]
  suggestions: PlanSuggestion[]
}

export interface TaskInput {
  title: string
  assigneeId: number
  rhythm: Rhythm
  startWeek: string
  criterionId?: number
}

// Backend error codes.
const planErrors: Record<string, ErrorKey> = {
  invalid_title: 'errors.planTitle',
  invalid_assignee: 'errors.planAssignee',
  invalid_rhythm: 'errors.planSave',
  invalid_criterion: 'errors.planCriterion',
  not_assignee: 'errors.planNotAssignee',
  not_due: 'errors.planNotDue',
}

async function failIfRejected(response: Response, fallback: ErrorKey) {
  if (response.status === 400 || response.status === 409) {
    let key = fallback
    try {
      const body = (await response.json()) as { code?: unknown }
      if (typeof body.code === 'string' && planErrors[body.code]) key = planErrors[body.code]
    } catch {
      // Keep the fallback message.
    }
    throw new UiError(key)
  }
  requireOk(response, fallback)
}

// 404 means the user has no flat.
export async function getPlan(week: string | null, signal: AbortSignal): Promise<Plan | null> {
  const query = week ? `?week=${encodeURIComponent(week)}` : ''
  const response = await api(`/api/wg/tasks${query}`, { signal })
  if (response.status === 404) return null
  return (await requireOk(response, 'errors.plan').json()) as Plan
}

export async function saveTask(id: number | null, task: TaskInput, signal: AbortSignal): Promise<void> {
  const response = await api(id === null ? '/api/wg/tasks' : `/api/wg/tasks/${id}`, {
    method: id === null ? 'POST' : 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(task),
    signal,
  })
  await failIfRejected(response, 'errors.planSave')
}

export async function deleteTask(id: number, signal: AbortSignal): Promise<void> {
  await failIfRejected(await api(`/api/wg/tasks/${id}`, { method: 'DELETE', signal }), 'errors.planSave')
}

export async function setDone(id: number, done: boolean, signal: AbortSignal): Promise<void> {
  const response = await api(`/api/wg/tasks/${id}/completion`, { method: done ? 'PUT' : 'DELETE', signal })
  await failIfRejected(response, 'errors.planSave')
}
