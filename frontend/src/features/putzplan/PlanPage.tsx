import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Form, Link, useActionData, useFetcher, useLoaderData, useNavigate, useNavigation, useSearchParams, useSubmit,
} from 'react-router'
import LoadingPage from '@/components/LoadingPage'
import BottomSheet from './BottomSheet'
import type { PlanActionResult } from './actions'
import { rhythms, type Plan, type PlanSuggestion, type PlanTask } from './api'
import { planPath } from './paths'
import type { PlanData } from './routes'
import './putzplan.css'

function shiftDays(day: string, days: number) {
  const date = new Date(`${day}T00:00:00Z`)
  date.setUTCDate(date.getUTCDate() + days)
  return date.toISOString().slice(0, 10)
}

const shiftWeeks = (week: string, weeks: number) => shiftDays(week, weeks * 7)

// ISO week number, based on the week's Thursday.
function calendarWeek(monday: string) {
  const thursday = new Date(`${monday}T00:00:00Z`)
  thursday.setUTCDate(thursday.getUTCDate() + 3)
  const yearStart = Date.UTC(thursday.getUTCFullYear(), 0, 1)
  return Math.ceil(((thursday.getTime() - yearStart) / 86400000 + 1) / 7)
}

function planUrl(week: string, extra: Record<string, string> = {}) {
  return `${planPath}?${new URLSearchParams({ week, ...extra })}`
}

// Filter by typed text, or show only the selected suggestion.
function visibleSuggestions(suggestions: PlanSuggestion[], title: string, selected: number | null) {
  if (selected !== null) return suggestions.filter((suggestion) => suggestion.criterionId === selected)
  const query = title.trim().toLowerCase()
  return suggestions.filter((suggestion) => `${suggestion.area} ${suggestion.title}`.toLowerCase().includes(query))
}

function TaskForm({ plan, task, onClose }: { plan: Plan; task?: PlanTask; onClose: () => void }) {
  const { t } = useTranslation()
  const result = useActionData<PlanActionResult | undefined>()
  const saving = useNavigation().state === 'submitting'
  const [title, setTitle] = useState(task?.title ?? '')
  const [criterionId, setCriterionId] = useState<number | null>(null)
  const shown = task ? [] : visibleSuggestions(plan.suggestions, title, criterionId)
  const areas = [...new Set(shown.map((suggestion) => suggestion.area))]

  function toggle(suggestion: PlanSuggestion) {
    const selected = suggestion.criterionId === criterionId
    setCriterionId(selected ? null : suggestion.criterionId)
    setTitle(selected ? '' : suggestion.title)
  }

  return (
    <BottomSheet title={task ? t('plan.editTask') : t('plan.addTask')} busy={saving} onClose={onClose}>
      <Form method="post" aria-label={task ? t('plan.editTask') : t('plan.addTask')}>
        <input type="hidden" name="intent" value="save" />
        {task && <input type="hidden" name="id" value={task.id} />}
        {criterionId !== null && <input type="hidden" name="criterionId" value={criterionId} />}
        <div className="fld">
          <label htmlFor="plan-title">{t('plan.task')}</label>
          <input id="plan-title" name="title" required maxLength={120} value={title} autoComplete="off"
            placeholder={t('plan.taskPlaceholder')}
            onChange={(event) => { setTitle(event.target.value); setCriterionId(null) }} />
        </div>
        {shown.length > 0 && (
          <div className="plan-suggestions" role="group" aria-label={t('plan.fromChecklist')}>
            {areas.map((area) => (
              <div key={area}>
                <div className="xs mut plan-area">{area}</div>
                <div className="plan-chips">
                  {shown.filter((suggestion) => suggestion.area === area).map((suggestion) => (
                    <button key={suggestion.criterionId} type="button" className="btn sm sec"
                      aria-pressed={suggestion.criterionId === criterionId} onClick={() => toggle(suggestion)}>
                      {suggestion.title}
                    </button>
                  ))}
                </div>
              </div>
            ))}
          </div>
        )}
        <div className="fld">
          <label htmlFor="plan-assignee">{t('plan.person')}</label>
          <select id="plan-assignee" name="assigneeId" required defaultValue={task?.assigneeId ?? plan.me}>
            {plan.members.map((member) => <option key={member.id} value={member.id}>{member.name}</option>)}
          </select>
        </div>
        <div className="fld">
          <label htmlFor="plan-rhythm">{t('plan.rhythm')}</label>
          <select id="plan-rhythm" name="rhythm" defaultValue={task?.rhythm ?? 'WEEKLY'}>
            {rhythms.map((rhythm) => <option key={rhythm} value={rhythm}>{t(`plan.rhythms.${rhythm}`)}</option>)}
          </select>
        </div>
        <div className="fld">
          <label htmlFor="plan-week">{t('plan.startWeek')}</label>
          <input id="plan-week" name="startWeek" type="date" required defaultValue={task?.startWeek ?? plan.week} />
        </div>
        {result && <p className="err" role="alert">{t(result.error)}</p>}
        <div className="plan-sheet-actions">
          <button className="btn gh" type="button" disabled={saving} onClick={onClose}>{t('plan.cancel')}</button>
          <button className="btn" type="submit" disabled={saving}>{saving ? t('plan.saving') : t('plan.save')}</button>
        </div>
      </Form>
    </BottomSheet>
  )
}

function DeleteSheet({ task, week, onClose }: { task: PlanTask; week: string; onClose: () => void }) {
  const { t } = useTranslation()
  const fetcher = useFetcher<PlanActionResult>()
  const busy = fetcher.state !== 'idle'
  return (
    <BottomSheet title={t('plan.deleteTitle')} busy={busy} onClose={onClose}>
      <p className="mut">{task.title}</p>
      {fetcher.data?.error && <p className="err" role="alert">{t(fetcher.data.error)}</p>}
      <fetcher.Form method="post" action={planUrl(week)}>
        <input type="hidden" name="intent" value="delete" />
        <input type="hidden" name="id" value={task.id} />
        <div className="plan-sheet-actions">
          <button className="btn gh" type="button" disabled={busy} onClick={onClose}>{t('plan.cancel')}</button>
          <button className="btn dg" type="submit" disabled={busy}>{t('plan.delete')}</button>
        </div>
      </fetcher.Form>
    </BottomSheet>
  )
}

function TaskRow({ task, week, onDelete }: { task: PlanTask; week: string; onDelete: () => void }) {
  const { t } = useTranslation()
  const submit = useSubmit()
  const busy = useNavigation().state === 'submitting'

  return (
    <div className="row plan-row">
      <div>
        {task.checkable ? (
          <label className="chk">
            <input type="checkbox" checked={task.done} disabled={busy}
              onChange={() => submit({ intent: 'toggle', id: task.id, done: String(!task.done) }, { method: 'post' })} />
            <b>{task.title}</b>
          </label>
        ) : <b>{task.title}</b>}
        <div className="xs mut">
          {t(`plan.rhythms.${task.rhythm}`)}
          {task.done && ` · ${task.doneBy ? t('plan.doneBy', { name: task.doneBy }) : t('plan.done')}`}
        </div>
      </div>
      <div className="sw">
        <span className={task.mine ? 'chip t' : 'chip'}>{task.assigneeName ?? t('plan.unassigned')}</span>
        <Link className="btn sm gh" to={planUrl(week, { edit: String(task.id) })}
          aria-label={t('plan.editFor', { title: task.title })}>✎</Link>
        <button className="btn sm gh" type="button" aria-label={t('plan.deleteFor', { title: task.title })}
          onClick={onDelete}>🗑</button>
      </div>
    </div>
  )
}

export default function PlanPage() {
  const { t, i18n } = useTranslation()
  const data = useLoaderData<PlanData | null>()
  const result = useActionData<PlanActionResult | undefined>()
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const [deletingId, setDeletingId] = useState<number | null>(null)

  if (!data) return <LoadingPage />
  if (!data.plan) {
    return (
      <section className="card empty">
        <div className="ic" aria-hidden="true">🧽</div>
        <h2>{t('navigation.cleaningPlan')}</h2>
        <p className="mut">{t('plan.noFlat')}</p>
        <Link className="btn" to="/student/flat">{t('wg.join')}</Link>
      </section>
    )
  }

  const { plan } = data
  const edit = params.get('edit')
  const editing = plan.tasks.find((task) => String(task.id) === edit)
  // The sheet closes once the task is gone.
  const deleting = plan.tasks.find((task) => task.id === deletingId)
  const closeForm = () => navigate(planUrl(plan.week))
  const done = plan.tasks.filter((task) => task.done).length
  const percent = plan.tasks.length ? Math.round((100 * done) / plan.tasks.length) : 0
  const format = new Intl.DateTimeFormat(i18n.resolvedLanguage, { day: '2-digit', month: '2-digit', timeZone: 'UTC' })
  const day = (value: string) => format.format(new Date(`${value}T00:00:00Z`))

  return (
    <>
      <div className="ch plan-toolbar">
        <div className="sw">
          <Link className="btn sm gh" to={planUrl(shiftWeeks(plan.week, -1))} aria-label={t('plan.previousWeek')}>‹</Link>
          <b className="plan-week">
            {t('plan.week', { week: calendarWeek(plan.week), from: day(plan.week), to: day(shiftDays(plan.week, 6)) })}
          </b>
          <Link className="btn sm gh" to={planUrl(shiftWeeks(plan.week, 1))} aria-label={t('plan.nextWeek')}>›</Link>
        </div>
        <Link className="btn sm" to={planUrl(plan.week, { edit: 'new' })}>{t('plan.add')}</Link>
      </div>

      {!edit && result && <p className="err" role="alert">{t(result.error)}</p>}

      <section className="card">
        <h3>{t('plan.tasks')}</h3>
        {plan.tasks.length === 0
          ? <p className="mut">{plan.allTitles.length === 0 ? t('plan.empty') : t('plan.noneThisWeek')}</p>
          : plan.tasks.map((task) => (
            <TaskRow key={task.id} task={task} week={plan.week} onDelete={() => setDeletingId(task.id)} />
          ))}
        {plan.allTitles.length === 0 && !edit && (
          <div className="acts"><Link className="btn sm" to={planUrl(plan.week, { edit: 'new' })}>{t('plan.addFirst')}</Link></div>
        )}
        <div className="pb" role="progressbar" aria-valuenow={percent} aria-valuemin={0} aria-valuemax={100}
          aria-label={t('plan.progressLabel')}><i style={{ width: `${percent}%` }} /></div>
        <div className="xs mut">{t('plan.progress', { done, total: plan.tasks.length })}</div>
        {plan.week !== plan.currentWeek && <div className="xs mut">{t('plan.onlyCurrentWeek')}</div>}
      </section>

      {edit === 'new' && <TaskForm key="new" plan={plan} onClose={closeForm} />}
      {editing && <TaskForm key={editing.id} plan={plan} task={editing} onClose={closeForm} />}
      {deleting && <DeleteSheet key={deleting.id} task={deleting} week={plan.week} onClose={() => setDeletingId(null)} />}
    </>
  )
}
