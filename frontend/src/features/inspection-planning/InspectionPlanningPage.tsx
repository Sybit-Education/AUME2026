import { useEffect, useId, useRef, useState, type PropsWithChildren } from 'react'
import { useTranslation } from 'react-i18next'
import { Form, Link, useActionData, useLoaderData, useNavigate, useNavigation, useSearchParams } from 'react-router'
import LoadingPage from '@/components/LoadingPage'
import type { PlanningActionResult } from './actions'
import type { InspectionPeriod, PeriodStatus, PlanningData, ResidenceOption, StaffMember } from './api'
import './inspection-planning.css'

const statusChip: Record<PeriodStatus, string> = {
  WAITING_FOR_CARETAKER: 'wr',
  COUNTER_PROPOSED: 'bad',
  AGREED: 'ok',
}

const FULL_DATE: Intl.DateTimeFormatOptions = { day: '2-digit', month: '2-digit', year: 'numeric' }
const SHORT_DATE: Intl.DateTimeFormatOptions = { day: '2-digit', month: '2-digit' }

function formatRange(from: string, to: string, language: string, format = FULL_DATE) {
  const date = (value: string) => new Date(`${value}T00:00:00Z`)
  return new Intl.DateTimeFormat(language, { ...format, timeZone: 'UTC' }).formatRange(date(from), date(to))
}

function formatWindow(start: string | null, end: string | null) {
  return start && end ? `${start.slice(0, 5)} – ${end.slice(0, 5)}` : '–'
}

function urlWith(params: URLSearchParams, changes: Record<string, string | null>) {
  const next = new URLSearchParams(params)
  next.delete('saved')
  Object.entries(changes).forEach(([key, value]) => {
    if (value === null) next.delete(key)
    else next.set(key, value)
  })
  const query = next.toString()
  return query ? `/inspection-planning?${query}` : '/inspection-planning'
}

interface PeriodDefaults {
  startsOn: string
  endsOn: string
  windowStart: string
  windowEnd: string
  caretakerId: number | null
}

function PeriodFields({ prefix, defaults, caretakers, caretakerKey }: {
  prefix: string
  defaults: PeriodDefaults
  caretakers: StaffMember[]
  caretakerKey?: number
}) {
  const { t } = useTranslation()
  return (
    <>
      <div className="g2 planning-fields">
        <div className="fld">
          <label htmlFor={`${prefix}-starts`}>{t('inspectionPlanning.startsOn')}</label>
          <input id={`${prefix}-starts`} name="startsOn" type="date" required defaultValue={defaults.startsOn} />
        </div>
        <div className="fld">
          <label htmlFor={`${prefix}-ends`}>{t('inspectionPlanning.endsOn')}</label>
          <input id={`${prefix}-ends`} name="endsOn" type="date" required defaultValue={defaults.endsOn} />
        </div>
        <div className="fld">
          <label htmlFor={`${prefix}-window-start`}>{t('inspectionPlanning.windowStart')}</label>
          <input id={`${prefix}-window-start`} name="windowStart" type="time" defaultValue={defaults.windowStart} />
        </div>
        <div className="fld">
          <label htmlFor={`${prefix}-window-end`}>{t('inspectionPlanning.windowEnd')}</label>
          <input id={`${prefix}-window-end`} name="windowEnd" type="time" defaultValue={defaults.windowEnd} />
        </div>
      </div>
      <div className="fld">
        <label htmlFor={`${prefix}-caretaker`}>{t('inspectionPlanning.caretaker')}</label>
        <select key={caretakerKey} id={`${prefix}-caretaker`} name="caretakerId" required defaultValue={defaults.caretakerId ?? ''}>
          {defaults.caretakerId === null && <option value="" disabled>{t('inspectionPlanning.chooseCaretaker')}</option>}
          {caretakers.map((person) => <option key={person.id} value={person.id}>{person.name}</option>)}
        </select>
      </div>
    </>
  )
}

function NewPeriodForm({ residences, caretakers, residence, error, onResidence }: {
  residences: ResidenceOption[]
  caretakers: StaffMember[]
  residence: ResidenceOption
  error?: PlanningActionResult
  onResidence: (id: string) => void
}) {
  const { t } = useTranslation()
  const saving = useNavigation().state === 'submitting'
  const open = residence.flatsWithOpenInspection
  const noneFree = residence.flatCount === 0 || open.length >= residence.flatCount
  return (
    <Form method="post" aria-label={t('inspectionPlanning.newPeriod')}>
      <input type="hidden" name="intent" value="create" />
      <div className="fld">
        <label htmlFor="new-residence">{t('inspectionPlanning.residence')}</label>
        <select id="new-residence" name="residenceId" value={residence.id} onChange={(event) => onResidence(event.target.value)}>
          {residences.map((option) => <option key={option.id} value={option.id}>{option.name}</option>)}
        </select>
        <div className="hint">{t('inspectionPlanning.flatCount', { count: residence.flatCount })}</div>
      </div>
      <div className="fld">
        <label htmlFor="new-kind">{t('inspectionPlanning.kindLabel')}</label>
        <select id="new-kind" disabled defaultValue="REGULAR">
          <option value="REGULAR">{t('inspectionPlanning.kind.REGULAR')}</option>
        </select>
      </div>
      <PeriodFields
        prefix="new"
        caretakers={caretakers}
        caretakerKey={residence.id}
        defaults={{ startsOn: '', endsOn: '', windowStart: '09:00', windowEnd: '16:00', caretakerId: residence.caretakerId }}
      />
      {open.length > 0 && (
        <p className="note wr" role="note">
          {t(noneFree ? 'inspectionPlanning.allFlatsOpen' : 'inspectionPlanning.openFlats', { flats: open.join(', ') })}
        </p>
      )}
      {error && <p className="err" role="alert">{t(error.error)}</p>}
      <div className="acts">
        <button className="btn" type="submit" disabled={saving || noneFree}>{t('inspectionPlanning.send')}</button>
      </div>
    </Form>
  )
}

function Dialog({ title, onClose, children }: PropsWithChildren<{ title: string; onClose: () => void }>) {
  const titleId = useId()
  const dialog = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const previous = document.activeElement
    dialog.current?.focus()
    return () => { if (previous instanceof HTMLElement) previous.focus() }
  }, [])

  return (
    <div className="mbg"
      onClick={(event) => { if (event.target === event.currentTarget) onClose() }}
      onKeyDown={(event) => { if (event.key === 'Escape') onClose() }}>
      <div ref={dialog} className="mdl planning-dialog" role="dialog" aria-modal="true" aria-labelledby={titleId} tabIndex={-1}>
        <h3 id={titleId}>{title}</h3>
        {children}
      </div>
    </div>
  )
}

function EditDialog({ period, caretakers, error, closeUrl, onClose }: {
  period: InspectionPeriod
  caretakers: StaffMember[]
  error?: PlanningActionResult
  closeUrl: string
  onClose: () => void
}) {
  const { t } = useTranslation()
  const saving = useNavigation().state === 'submitting'
  return (
    <Dialog title={t('inspectionPlanning.editTitle', { name: period.residenceName })} onClose={onClose}>
      <Form method="post" aria-label={t('inspectionPlanning.editFor', { name: period.residenceName })}>
        <input type="hidden" name="intent" value="update" />
        <input type="hidden" name="periodId" value={period.id} />
        <input type="hidden" name="version" value={period.version} />
        <PeriodFields
          prefix={`edit-${period.id}`}
          caretakers={caretakers}
          defaults={{
            startsOn: period.startsOn,
            endsOn: period.endsOn,
            windowStart: period.windowStart?.slice(0, 5) ?? '',
            windowEnd: period.windowEnd?.slice(0, 5) ?? '',
            caretakerId: period.caretaker?.id ?? null,
          }}
        />
        <p className="hint">{t('inspectionPlanning.editHint')}</p>
        {error && <p className="err" role="alert">{t(error.error)}</p>}
        <div className="ft">
          <Link className="btn gh" to={closeUrl} replace>{t('inspectionPlanning.cancel')}</Link>
          <button className="btn" type="submit" disabled={saving}>{t('inspectionPlanning.sendAgain')}</button>
        </div>
      </Form>
    </Dialog>
  )
}

function ReviewDialog({ period, counter, error, closeUrl, editUrl, onClose }: {
  period: InspectionPeriod
  counter: NonNullable<InspectionPeriod['counter']>
  error?: PlanningActionResult
  closeUrl: string
  editUrl: string
  onClose: () => void
}) {
  const { t, i18n } = useTranslation()
  const saving = useNavigation().state === 'submitting'
  const language = i18n.resolvedLanguage ?? 'de'
  return (
    <Dialog title={t('inspectionPlanning.reviewTitle', { name: period.residenceName })} onClose={onClose}>
      <div className="tw planning-compare">
        <table className="tbl">
          <thead>
            <tr>
              <th scope="col" />
              <th scope="col">{t('inspectionPlanning.current')}</th>
              <th scope="col">{t('inspectionPlanning.proposed')}</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td className="b">{t('inspectionPlanning.period')}</td>
              <td>{formatRange(period.startsOn, period.endsOn, language)}</td>
              <td className="b">{formatRange(counter.startsOn, counter.endsOn, language)}</td>
            </tr>
            <tr>
              <td className="b">{t('inspectionPlanning.time')}</td>
              <td>{formatWindow(period.windowStart, period.windowEnd)}</td>
              <td className="b">{formatWindow(counter.windowStart, counter.windowEnd)}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <p className="planning-reason"><b>{t('inspectionPlanning.reason')}:</b> {counter.reason}</p>
      {error && <p className="err" role="alert">{t(error.error)}</p>}
      <Form method="post">
        <input type="hidden" name="intent" value="accept" />
        <input type="hidden" name="periodId" value={period.id} />
        <input type="hidden" name="version" value={period.version} />
        <div className="ft">
          <Link className="btn gh" to={closeUrl} replace>{t('inspectionPlanning.cancel')}</Link>
          <Link className="btn sec" to={editUrl} replace>{t('inspectionPlanning.newPeriodShort')}</Link>
          <button className="btn" type="submit" disabled={saving}>{t('inspectionPlanning.accept')}</button>
        </div>
      </Form>
    </Dialog>
  )
}

function WithdrawDialog({ period, onClose }: { period: InspectionPeriod; onClose: () => void }) {
  const { t } = useTranslation()
  return (
    <Dialog title={t('inspectionPlanning.withdrawTitle')} onClose={onClose}>
      <p>{t('inspectionPlanning.withdrawConfirm', { name: period.residenceName })}</p>
      <Form method="post" onSubmit={onClose}>
        <input type="hidden" name="intent" value="withdraw" />
        <input type="hidden" name="periodId" value={period.id} />
        <input type="hidden" name="version" value={period.version} />
        <div className="ft">
          <button className="btn gh" type="button" onClick={onClose}>{t('inspectionPlanning.cancel')}</button>
          <button className="btn dg" type="submit">{t('inspectionPlanning.withdraw')}</button>
        </div>
      </Form>
    </Dialog>
  )
}

function PeriodRow({ period, actionUrl, onWithdraw }: {
  period: InspectionPeriod
  actionUrl: string
  onWithdraw: (id: number) => void
}) {
  const { t, i18n } = useTranslation()
  const language = i18n.resolvedLanguage ?? 'de'
  const announced = period.announcedAt !== null
  const counter = period.counter
  return (
    <tr>
      <td>
        <b>{period.residenceName}</b>
        <div className="xs mut">{t('inspectionPlanning.flats', { count: period.flatCount })}</div>
      </td>
      <td className="planning-period">
        {formatRange(period.startsOn, period.endsOn, language)}
        {counter
          ? <div className="xs b">{t('inspectionPlanning.newShort')}: {formatRange(counter.startsOn, counter.endsOn, language, SHORT_DATE)}</div>
          : period.windowStart && period.windowEnd && <div className="xs mut">{formatWindow(period.windowStart, period.windowEnd)}</div>}
      </td>
      <td>{period.caretaker?.name ?? <span className="mut">{t('inspectionPlanning.noCaretaker')}</span>}</td>
      <td>
        {announced
          ? <span className="chip t">{t('inspectionPlanning.announced')}</span>
          : <span className={`chip ${statusChip[period.status]}`}>{t(`inspectionPlanning.status.${period.status}`)}</span>}
      </td>
      <td>
        {!announced && (
          <div className="planning-actions">
            <Link className={counter ? 'btn sm' : 'btn gh sm'} to={actionUrl}
              aria-label={t(counter ? 'inspectionPlanning.reviewFor' : 'inspectionPlanning.editFor', { name: period.residenceName })}>
              {t(counter ? 'inspectionPlanning.review' : 'inspectionPlanning.edit')}
            </Link>
            <button className="btn gh sm" type="button" title={t('inspectionPlanning.withdraw')}
              aria-label={t('inspectionPlanning.withdrawFor', { name: period.residenceName })}
              onClick={() => onWithdraw(period.id)}>
              <span aria-hidden="true">🗑</span>
            </button>
          </div>
        )}
      </td>
    </tr>
  )
}

export default function InspectionPlanningPage() {
  const { t } = useTranslation()
  const data = useLoaderData<PlanningData | null>()
  const result = useActionData<PlanningActionResult | undefined>()
  const [params, setParams] = useSearchParams()
  const navigate = useNavigate()
  const [withdrawId, setWithdrawId] = useState<number | null>(null)

  if (!data) return <LoadingPage />

  const { options, periods } = data
  const residence = options.residences.find((option) => String(option.id) === params.get('residence'))
    ?? options.residences.find((option) => option.flatCount > option.flatsWithOpenInspection.length)
    ?? options.residences[0]
  const saved = params.get('saved')
  const savedKey = saved === 'created' || saved === 'updated' || saved === 'accepted' || saved === 'withdrawn' ? saved : null
  const page = periods.page
  const openPeriod = (id: string | null) => periods.content.find((period) => String(period.id) === id && period.announcedAt === null)
  const editing = openPeriod(params.get('edit'))
  const reviewing = openPeriod(params.get('review'))
  const withdrawing = periods.content.find((period) => period.id === withdrawId && period.announcedAt === null)
  const closeUrl = urlWith(params, { edit: null, review: null })
  const closeDialog = () => { void navigate(closeUrl, { replace: true }) }
  const editUrl = (id: number) => urlWith(params, { edit: String(id), review: null })
  const reviewUrl = (id: number) => urlWith(params, { review: String(id), edit: null })

  function chooseResidence(id: string) {
    const next = new URLSearchParams(params)
    next.delete('saved')
    next.set('residence', id)
    setParams(next, { replace: true })
  }

  return (
    <>
      <div className="ch"><h2>{t('inspectionPlanning.title')}</h2></div>
      {savedKey && <p className="note ok" role="status">{t(`inspectionPlanning.saved.${savedKey}`)}</p>}
      <div className="planning-grid">
        <section className="card">
          <h3>{t('inspectionPlanning.newPeriod')}</h3>
          {residence ? (
            <NewPeriodForm
              key={page.totalElements}
              residences={options.residences}
              caretakers={options.caretakers}
              residence={residence}
              error={result?.intent === 'create' ? result : undefined}
              onResidence={chooseResidence}
            />
          ) : <p className="mut">{t('inspectionPlanning.noResidences')}</p>}
        </section>
        <section className="card">
          <h3>{t('inspectionPlanning.planned')}</h3>
          {result?.intent === 'withdraw' && <p className="err" role="alert">{t(result.error)}</p>}
          {periods.content.length === 0 ? <p className="mut">{t('inspectionPlanning.noPeriods')}</p> : (
            <div className="tw">
              <table className="tbl" aria-label={t('inspectionPlanning.planned')}>
                <thead>
                  <tr>
                    <th scope="col">{t('inspectionPlanning.residence')}</th>
                    <th scope="col">{t('inspectionPlanning.period')}</th>
                    <th scope="col">{t('inspectionPlanning.caretaker')}</th>
                    <th scope="col">{t('inspectionPlanning.statusLabel')}</th>
                    <th scope="col" className="r">{t('inspectionPlanning.actions')}</th>
                  </tr>
                </thead>
                <tbody>
                  {periods.content.map((period) => (
                    <PeriodRow
                      key={period.id}
                      period={period}
                      actionUrl={period.counter ? reviewUrl(period.id) : editUrl(period.id)}
                      onWithdraw={setWithdrawId}
                    />
                  ))}
                </tbody>
              </table>
            </div>
          )}
          {page.totalPages > 1 && (
            <nav className="acts" aria-label={t('pages.pagination')}>
              <Link className={`btn sec${page.number === 0 ? ' disabled' : ''}`} aria-disabled={page.number === 0}
                to={urlWith(params, { page: String(Math.max(page.number - 1, 0)), edit: null, review: null })}>{t('pages.previous')}</Link>
              <Link className={`btn sec${page.number + 1 >= page.totalPages ? ' disabled' : ''}`} aria-disabled={page.number + 1 >= page.totalPages}
                to={urlWith(params, { page: String(Math.min(page.number + 1, page.totalPages - 1)), edit: null, review: null })}>{t('pages.next')}</Link>
            </nav>
          )}
        </section>
      </div>
      {editing && (
        <EditDialog
          key={editing.id}
          period={editing}
          caretakers={options.caretakers}
          error={result?.intent === 'update' ? result : undefined}
          closeUrl={closeUrl}
          onClose={closeDialog}
        />
      )}
      {reviewing?.counter && (
        <ReviewDialog
          period={reviewing}
          counter={reviewing.counter}
          error={result?.intent === 'accept' ? result : undefined}
          closeUrl={closeUrl}
          editUrl={editUrl(reviewing.id)}
          onClose={closeDialog}
        />
      )}
      {withdrawing && <WithdrawDialog period={withdrawing} onClose={() => setWithdrawId(null)} />}
    </>
  )
}
