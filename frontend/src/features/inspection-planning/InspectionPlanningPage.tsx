import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Form, Link, useActionData, useLoaderData, useNavigation, useSearchParams } from 'react-router'
import LoadingPage from '@/components/LoadingPage'
import type { PlanningActionResult } from './actions'
import type { InspectionPeriod, PeriodStatus, PlanningData, ResidenceOption, StaffMember } from './api'
import './inspection-planning.css'

const statusChip: Record<PeriodStatus, string> = {
  WAITING_FOR_CARETAKER: 'wr',
  COUNTER_PROPOSED: 'bad',
  AGREED: 'ok',
}

function formatDate(value: string, language: string) {
  return new Intl.DateTimeFormat(language, { dateStyle: 'medium', timeZone: 'UTC' }).format(new Date(`${value}T00:00:00Z`))
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

function EditRow({ period, caretakers, error, cancelUrl }: {
  period: InspectionPeriod
  caretakers: StaffMember[]
  error?: PlanningActionResult
  cancelUrl: string
}) {
  const { t } = useTranslation()
  const saving = useNavigation().state === 'submitting'
  return (
    <tr>
      <td colSpan={5}>
        <Form method="post" aria-label={t('inspectionPlanning.editFor', { name: period.residenceName })}>
          <input type="hidden" name="intent" value="update" />
          <input type="hidden" name="periodId" value={period.id} />
          <input type="hidden" name="version" value={period.version} />
          <p className="b">{period.residenceName}</p>
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
          <div className="acts">
            <button className="btn sm" type="submit" disabled={saving}>{t('inspectionPlanning.sendAgain')}</button>
            <Link className="btn gh sm" to={cancelUrl}>{t('inspectionPlanning.cancel')}</Link>
          </div>
        </Form>
      </td>
    </tr>
  )
}

function PeriodRow({ period, editUrl, onWithdraw }: {
  period: InspectionPeriod
  editUrl: string
  onWithdraw: (id: number) => void
}) {
  const { t, i18n } = useTranslation()
  const saving = useNavigation().state === 'submitting'
  const language = i18n.resolvedLanguage ?? 'de'
  const announced = period.announcedAt !== null
  const range = (from: string, to: string) => `${formatDate(from, language)} – ${formatDate(to, language)}`
  return (
    <tr>
      <td>
        <b>{period.residenceName}</b>
        <div className="xs mut">{t('inspectionPlanning.kind.REGULAR')} · {t('inspectionPlanning.flats', { count: period.flatCount })}</div>
      </td>
      <td>
        {range(period.startsOn, period.endsOn)}
        {period.windowStart && period.windowEnd && (
          <div className="xs mut">{period.windowStart.slice(0, 5)} – {period.windowEnd.slice(0, 5)}</div>
        )}
        {period.counter && (
          <div className="xs">
            <b>{t('inspectionPlanning.counterProposal')}:</b> {range(period.counter.startsOn, period.counter.endsOn)}
            {period.counter.windowStart && period.counter.windowEnd && (
              <div className="mut">{period.counter.windowStart.slice(0, 5)} – {period.counter.windowEnd.slice(0, 5)}</div>
            )}
            <div className="mut">{period.counter.reason}</div>
          </div>
        )}
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
            {period.status === 'COUNTER_PROPOSED' && (
              <Form method="post">
                <input type="hidden" name="intent" value="accept" />
                <input type="hidden" name="periodId" value={period.id} />
                <input type="hidden" name="version" value={period.version} />
                <button className="btn sm" type="submit" disabled={saving}
                  aria-label={t('inspectionPlanning.acceptFor', { name: period.residenceName })}>
                  {t('inspectionPlanning.accept')}
                </button>
              </Form>
            )}
            <Link className="btn gh sm" to={editUrl} aria-label={t('inspectionPlanning.editFor', { name: period.residenceName })}>
              {t(period.status === 'COUNTER_PROPOSED' ? 'inspectionPlanning.newPeriodShort' : 'inspectionPlanning.edit')}
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

function WithdrawDialog({ period, onClose }: { period: InspectionPeriod; onClose: () => void }) {
  const { t } = useTranslation()
  return (
    <div className="mbg"
      onClick={(event) => { if (event.target === event.currentTarget) onClose() }}
      onKeyDown={(event) => { if (event.key === 'Escape') onClose() }}>
      <div className="mdl" role="dialog" aria-modal="true" aria-labelledby="withdraw-title">
        <h3 id="withdraw-title">{t('inspectionPlanning.withdrawTitle')}</h3>
        <p>{t('inspectionPlanning.withdrawConfirm', { name: period.residenceName })}</p>
        <Form method="post" onSubmit={onClose}>
          <input type="hidden" name="intent" value="withdraw" />
          <input type="hidden" name="periodId" value={period.id} />
          <input type="hidden" name="version" value={period.version} />
          <div className="ft">
            <button className="btn gh" type="button" autoFocus onClick={onClose}>{t('inspectionPlanning.cancel')}</button>
            <button className="btn dg" type="submit">{t('inspectionPlanning.withdraw')}</button>
          </div>
        </Form>
      </div>
    </div>
  )
}

export default function InspectionPlanningPage() {
  const { t } = useTranslation()
  const data = useLoaderData<PlanningData | null>()
  const result = useActionData<PlanningActionResult | undefined>()
  const [params, setParams] = useSearchParams()
  const [withdrawId, setWithdrawId] = useState<number | null>(null)

  if (!data) return <LoadingPage />

  const { options, periods } = data
  const residence = options.residences.find((option) => String(option.id) === params.get('residence'))
    ?? options.residences.find((option) => option.flatCount > option.flatsWithOpenInspection.length)
    ?? options.residences[0]
  const saved = params.get('saved')
  const savedKey = saved === 'created' || saved === 'updated' || saved === 'accepted' || saved === 'withdrawn' ? saved : null
  const editing = params.get('edit')
  const page = periods.page
  const withdrawing = periods.content.find((period) => period.id === withdrawId && period.announcedAt === null)

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
          {(result?.intent === 'accept' || result?.intent === 'withdraw') && <p className="err" role="alert">{t(result.error)}</p>}
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
                    String(period.id) === editing && period.announcedAt === null
                      ? <EditRow key={period.id} period={period} caretakers={options.caretakers}
                          error={result?.intent === 'update' ? result : undefined} cancelUrl={urlWith(params, { edit: null })} />
                      : <PeriodRow key={period.id} period={period} editUrl={urlWith(params, { edit: String(period.id) })} onWithdraw={setWithdrawId} />
                  ))}
                </tbody>
              </table>
            </div>
          )}
          {page.totalPages > 1 && (
            <nav className="acts" aria-label={t('pages.pagination')}>
              <Link className={`btn sec${page.number === 0 ? ' disabled' : ''}`} aria-disabled={page.number === 0}
                to={urlWith(params, { page: String(Math.max(page.number - 1, 0)), edit: null })}>{t('pages.previous')}</Link>
              <Link className={`btn sec${page.number + 1 >= page.totalPages ? ' disabled' : ''}`} aria-disabled={page.number + 1 >= page.totalPages}
                to={urlWith(params, { page: String(Math.min(page.number + 1, page.totalPages - 1)), edit: null })}>{t('pages.next')}</Link>
            </nav>
          )}
        </section>
      </div>
      {withdrawing && <WithdrawDialog period={withdrawing} onClose={() => setWithdrawId(null)} />}
    </>
  )
}
