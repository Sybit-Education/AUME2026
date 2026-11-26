import { useId } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useFetcher } from 'react-router'
import type { PlanActionResult } from './actions'
import type { Plan, PlanTask } from './api'
import { planPath } from './paths'

/**
 * Eigene Putzaufgaben der aktuellen Woche für die Startseite (#99). Abgehakt wird über die Action der
 * Route, in der die Karte steht (`toggleTaskAction`); danach lädt die Seite ihre Daten neu.
 */
export default function WeekTasksCard({ plan, loadFailed = false, action }: {
  plan: Plan | null | undefined
  loadFailed?: boolean
  /** Adresse der Route mit `toggleTaskAction`. */
  action: string
}) {
  const { t } = useTranslation()
  const headingId = useId()
  // Ohne WG gibt es keinen Putzplan; den Hinweis zum Beitreten zeigt die Begrüßung.
  if (!loadFailed && !plan) return null
  const mine = plan?.tasks.filter((task) => task.mine) ?? []

  return (
    <section className="card" aria-labelledby={headingId}>
      <h2 id={headingId}>{t('home.week')}</h2>
      {loadFailed ? <p className="err" role="alert">{t('errors.plan')}</p> : (
        <>
          {mine.length
            ? mine.map((task) => <TaskCheck key={task.id} task={task} action={action} />)
            : <p className="mut">{t('home.noTasks')}</p>}
          <div className="acts">
            <Link className="btn sm gh" to={planPath}>{t('home.openPlan')}</Link>
          </div>
        </>
      )}
    </section>
  )
}

function TaskCheck({ task, action }: { task: PlanTask; action: string }) {
  const { t } = useTranslation()
  const fetcher = useFetcher<PlanActionResult | null>()
  const pending = fetcher.formData?.get('done')
  // Bis die Seite neu geladen ist, den gewünschten Zustand zeigen.
  const done = typeof pending === 'string' ? pending === 'true' : task.done
  const toggle = () => {
    void fetcher.submit({ intent: 'toggle', id: String(task.id), done: String(!done) }, { method: 'post', action })
  }

  return (
    <div className="row">
      <div>
        {task.checkable ? (
          <label className="chk">
            <input type="checkbox" checked={done} disabled={fetcher.state !== 'idle'} onChange={toggle} />
            <span>{task.title}</span>
          </label>
        ) : <span>{task.title}</span>}
        {fetcher.data?.error && <p className="err xs" role="alert">{t(fetcher.data.error)}</p>}
      </div>
      {done && <span className="chip ok">{t('plan.done')}</span>}
    </div>
  )
}
