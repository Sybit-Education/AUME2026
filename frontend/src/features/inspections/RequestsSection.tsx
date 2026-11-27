import { useEffect, useId, useRef, useState, type FormEvent, type PropsWithChildren } from 'react'
import { useTranslation } from 'react-i18next'
import { Form, Link, useActionData, useLocation, useNavigate, useNavigation, useSearchParams } from 'react-router'
import type { RequestActionResult } from './requestsActions'
import { MAX_REASON_LENGTH, type InspectionRequest, type RequestPage } from './requestsApi'
import { formatPeriod, formatWindow } from './dates'
import { ActiveFilters, FilterButton, FilterMenu, SortHeader, type ActiveFilter } from './tableControls'
import { compareValues, contains, overlaps, periodChipLabel, useFilterMenu, useLocalTable, type FilterSpec } from './tableState'
import './requests.css'

// Aufbau nach dem Wireframe (Objektbetreuung, „Anstehend“ und „Vorschlag beantworten“).

const statusChip: Record<InspectionRequest['status'], string> = {
  WAITING_FOR_CARETAKER: 'wr',
  COUNTER_PROPOSED: 'wr',
  AGREED: 'ok',
}

/** Adresse der aktuellen Seite mit geänderten Parametern (Dialog öffnen oder schließen). */
function urlWith(pathname: string, params: URLSearchParams, changes: Record<string, string | null>) {
  const next = new URLSearchParams(params)
  next.delete('saved')
  Object.entries(changes).forEach(([key, value]) => {
    if (value === null) next.delete(key)
    else next.set(key, value)
  })
  const query = next.toString()
  return query ? `${pathname}?${query}` : pathname
}

function Dialog({ title, onClose, closeUrl, children }: PropsWithChildren<{
  title: string
  onClose: () => void
  /** Mit Adresse erscheint oben rechts ein Schließen-Knopf. */
  closeUrl?: string
}>) {
  const { t } = useTranslation()
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
      <div ref={dialog} className="mdl requests-dialog" role="dialog" aria-modal="true" aria-labelledby={titleId} tabIndex={-1}>
        <div className="requests-dialog-head">
          <h3 id={titleId}>{title}</h3>
          {closeUrl && (
            <Link className="requests-close" to={closeUrl} replace aria-label={t('inspectionRequests.close')} title={t('inspectionRequests.close')}>
              <span aria-hidden="true">✕</span>
            </Link>
          )}
        </div>
        {children}
      </div>
    </div>
  )
}

/** „Vorschlag · Anlage“: Zeitraum ansehen, zustimmen oder zum Gegenvorschlag wechseln. */
function AnswerDialog({ request, error, closeUrl, counterUrl, onClose }: {
  request: InspectionRequest
  error?: RequestActionResult
  closeUrl: string
  counterUrl: string
  onClose: () => void
}) {
  const { t, i18n } = useTranslation()
  const saving = useNavigation().state === 'submitting'
  const hours = formatWindow(request.windowStart, request.windowEnd)
  return (
    <Dialog title={t('inspectionRequests.answerTitle', { name: request.residenceName })} onClose={onClose} closeUrl={closeUrl}>
      <dl className="requests-facts">
        <dt>{t('inspectionRequests.period')}</dt>
        <dd><b>{formatPeriod(request.startsOn, request.endsOn, i18n.resolvedLanguage)}</b></dd>
        {hours && <><dt>{t('inspectionRequests.time')}</dt><dd>{hours}</dd></>}
        <dt>{t('inspectionRequests.flatsLabel')}</dt>
        <dd>{t('inspectionRequests.flats', { count: request.flatCount })}{request.flatLabels && ` (${request.flatLabels.join(', ')})`}</dd>
      </dl>
      <p className="xs mut">{t('inspectionRequests.agreeHint')}</p>
      {error && <p className="err" role="alert">{t(error.error)}</p>}
      <Form method="post">
        <input type="hidden" name="intent" value="agree" />
        <input type="hidden" name="periodId" value={request.id} />
        <input type="hidden" name="version" value={request.version} />
        <div className="ft">
          <Link className="btn gh" to={counterUrl} replace>{t('inspectionRequests.counter')}</Link>
          <button className="btn" type="submit" disabled={saving}>{t('inspectionRequests.agree')}</button>
        </div>
      </Form>
    </Dialog>
  )
}

/** „Gegenvorschlag · Anlage“: Zeitraum von/bis und Grund. Die Uhrzeit bleibt die vorgeschlagene. */
function CounterDialog({ request, error, closeUrl, onClose }: {
  request: InspectionRequest
  error?: RequestActionResult
  closeUrl: string
  onClose: () => void
}) {
  const { t } = useTranslation()
  const saving = useNavigation().state === 'submitting'
  const prefix = `counter-${request.id}`
  const [reasonMissing, setReasonMissing] = useState(false)
  // Fehlt der Grund, steht der Hinweis direkt am Feld statt unten als Meldung.
  const reasonInvalid = reasonMissing || error?.error === 'errors.requestReasonRequired'
  const otherError = error && error.error !== 'errors.requestReasonRequired' ? error : undefined

  function checkReason(event: FormEvent<HTMLFormElement>) {
    const reason = event.currentTarget.elements.namedItem('reason') as HTMLTextAreaElement
    if (reason.value.trim() === '') {
      event.preventDefault()
      setReasonMissing(true)
      reason.focus()
    }
  }

  return (
    <Dialog title={t('inspectionRequests.counterTitle', { name: request.residenceName })} onClose={onClose}>
      <Form method="post" noValidate onSubmit={checkReason}
        aria-label={t('inspectionRequests.counterTitle', { name: request.residenceName })}>
        <input type="hidden" name="intent" value="counter" />
        <input type="hidden" name="periodId" value={request.id} />
        <input type="hidden" name="version" value={request.version} />
        <div className="fld">
          <label htmlFor={`${prefix}-starts`}>{t('inspectionRequests.startsOn')}</label>
          <input id={`${prefix}-starts`} name="startsOn" type="date" required defaultValue={request.startsOn} />
        </div>
        <div className="fld">
          <label htmlFor={`${prefix}-ends`}>{t('inspectionRequests.endsOn')}</label>
          <input id={`${prefix}-ends`} name="endsOn" type="date" required defaultValue={request.endsOn} />
        </div>
        <div className="fld">
          <label htmlFor={`${prefix}-reason`}>
            {t('inspectionRequests.reason')} <span className="requests-required" aria-hidden="true">*</span>
          </label>
          <textarea id={`${prefix}-reason`} name="reason" required maxLength={MAX_REASON_LENGTH} rows={3}
            className={reasonInvalid ? 'requests-invalid' : undefined} aria-invalid={reasonInvalid || undefined}
            aria-describedby={reasonInvalid ? `${prefix}-reason-error` : undefined}
            onChange={(event) => { if (event.target.value.trim() !== '') setReasonMissing(false) }} />
          {reasonInvalid && <p id={`${prefix}-reason-error`} className="requests-field-error">{t('errors.requestReasonRequired')}</p>}
        </div>
        {otherError && <p className="err" role="alert">{t(otherError.error)}</p>}
        <div className="ft">
          <Link className="btn gh" to={closeUrl} replace>{t('inspectionRequests.cancel')}</Link>
          <button className="btn" type="submit" disabled={saving}>{t('inspectionRequests.sendCounter')}</button>
        </div>
      </Form>
    </Dialog>
  )
}

function RequestRow({ request, answerUrl }: { request: InspectionRequest; answerUrl: string }) {
  const { t, i18n } = useTranslation()
  const counter = request.counter
  const hours = formatWindow(request.windowStart, request.windowEnd)
  return (
    <tr>
      <td>{request.residenceName}</td>
      <td>
        <b>{request.flatCount}</b>
        {request.flatLabels && <div className="xs mut">{request.flatLabels.join(', ')}</div>}
      </td>
      <td>{t(`inspectionRequests.kind.${request.kind}`)}</td>
      <td className="ins-period">
        {formatPeriod(request.startsOn, request.endsOn, i18n.resolvedLanguage)}
        {hours && <div className="xs mut">{hours}</div>}
        {counter && (
          <div className="xs">{t('inspectionRequests.yourProposal')}: {formatPeriod(counter.startsOn, counter.endsOn, i18n.resolvedLanguage)}</div>
        )}
      </td>
      <td><span className={`chip ${statusChip[request.status]}`}>{t(`inspectionRequests.status.${request.status}`)}</span></td>
      <td className="r">
        {request.status === 'WAITING_FOR_CARETAKER' && (
          <Link className="btn sm sec" to={answerUrl} aria-label={t('inspectionRequests.answerFor', { name: request.residenceName })}>
            {t('inspectionRequests.answer')}
          </Link>
        )}
      </td>
    </tr>
  )
}

type Column = 'residence' | 'flats' | 'kind' | 'period' | 'status'

const statusOrder: InspectionRequest['status'][] = ['WAITING_FOR_CARETAKER', 'COUNTER_PROPOSED', 'AGREED']

/**
 * Abschnitt „Anfragen“ auf der Seite Kontrollen, über „Anstehend“: vorgeschlagene Zeiträume beantworten (#44).
 * Sortieren und Filtern wie in der Historie, aber im Browser. Die Tabelle zeigt höchstens fünf Zeilen, weitere
 * erreicht man durch Scrollen in der Tabelle.
 */
export default function RequestsSection({ data }: { data: RequestPage }) {
  const { t, i18n } = useTranslation()
  const result = useActionData<RequestActionResult | undefined>()
  const [params] = useSearchParams()
  const { pathname } = useLocation()
  const navigate = useNavigate()
  const section = useRef<HTMLElement>(null)
  const menu = useFilterMenu<Column>(section)
  const table = useLocalTable<Column>()
  const { filters } = table
  const all = t('inspections.history.all')
  const label: Record<Column, string> = {
    residence: t('inspectionRequests.residence'), flats: t('inspectionRequests.flatsLabel'),
    kind: t('inspectionRequests.kindLabel'), period: t('inspectionRequests.period'), status: t('inspectionRequests.statusLabel'),
  }
  const statusText = (status: InspectionRequest['status']) => t(`inspectionRequests.status.${status}`)

  // Standard: offene Anfragen zuerst, dann die beantworteten, jeweils nach Beginn des Zeitraums.
  const open = data.content.filter((request) => request.status === 'WAITING_FOR_CARETAKER')
  const requests = data.content
    .filter((request) => contains(request.residenceName, filters.residence))
    .filter((request) => overlaps(request.startsOn, request.endsOn, filters.period))
    .filter((request) => !filters.status?.value || request.status === filters.status.value)
    .sort((a, b) => statusOrder.indexOf(a.status) - statusOrder.indexOf(b.status) || a.startsOn.localeCompare(b.startsOn))
  if (table.sort) {
    const { column, dir } = table.sort
    const key = (request: InspectionRequest): string | number => ({
      residence: request.residenceName, flats: request.flatCount, kind: request.kind, period: request.startsOn,
      status: statusOrder.indexOf(request.status),
    })[column]
    requests.sort((a, b) => compareValues(key(a), key(b), dir))
  }

  const spec: Partial<Record<Column, FilterSpec>> = {
    residence: { kind: 'text', label: label.residence, value: filters.residence?.text ?? '' },
    period: { kind: 'dates', label: label.period, from: filters.period?.from ?? '', to: filters.period?.to ?? '' },
    status: { kind: 'options', label: label.status, value: filters.status?.value ?? '', options: [
      ['', all], ...statusOrder.map((status): [string, string] => [status, statusText(status)]),
    ] },
  }

  const active: ActiveFilter[] = []
  const clear = (column: Column) => () => table.setFilter(column, {})
  if (filters.residence?.text) active.push({ key: 'residence', label: `${label.residence}: ${filters.residence.text}`, clear: clear('residence') })
  if (filters.period?.from || filters.period?.to) {
    active.push({ key: 'period', label: periodChipLabel(label.period, filters.period, i18n.resolvedLanguage), clear: clear('period') })
  }
  if (filters.status?.value) {
    active.push({ key: 'status', label: `${label.status}: ${statusText(filters.status.value as InspectionRequest['status'])}`, clear: clear('status') })
  }

  const header = (column: Column) => (
    <SortHeader label={label[column]} sorted={table.sortedBy(column)} onSort={() => table.toggleSort(column)}
      filterButton={spec[column] && (
        <FilterButton label={label[column]} active={Boolean(filters[column])} expanded={menu.open?.column === column}
          onClick={(button) => menu.toggle(column, button)} />
      )} />
  )

  const saved = params.get('saved')
  const savedKey = saved === 'agreed' || saved === 'countered' ? saved : null
  const find = (id: string | null) => open.find((request) => String(request.id) === id)
  const answering = find(params.get('answer'))
  const countering = find(params.get('counter'))
  const closeUrl = urlWith(pathname, params, { answer: null, counter: null })
  const closeDialog = () => { void navigate(closeUrl, { replace: true }) }
  const menuSpec = menu.open ? spec[menu.open.column] : undefined

  return (
    <section className="ins-section" ref={section} aria-labelledby="requests-title">
      <h3 id="requests-title">{t('inspectionRequests.title')}</h3>
      {savedKey && <p className="note ok" role="status">{t(`inspectionRequests.saved.${savedKey}`)}</p>}
      <ActiveFilters filters={active} onReset={table.reset} />

      {data.content.length === 0 ? <p className="mut">{t('inspectionRequests.none')}</p> : (
        <div className="tw ins-scroll">
          <table className="tbl ins-fixed" aria-label={t('inspectionRequests.title')}>
            <colgroup>
              <col style={{ width: '20%' }} />
              <col style={{ width: '10%' }} />
              <col style={{ width: '15%' }} />
              <col style={{ width: '21%' }} />
              <col style={{ width: '16%' }} />
              <col style={{ width: '18%' }} />
            </colgroup>
            <thead>
              <tr>
                {header('residence')}
                {header('flats')}
                {header('kind')}
                {header('period')}
                {header('status')}
                <th scope="col" />
              </tr>
            </thead>
            <tbody>
              {requests.length === 0 && (
                <tr><td colSpan={6} className="mut">{t('inspections.history.noMatch')}</td></tr>
              )}
              {requests.map((request) => (
                <RequestRow key={request.id} request={request}
                  answerUrl={urlWith(pathname, params, { answer: String(request.id), counter: null })} />
              ))}
            </tbody>
          </table>
        </div>
      )}

      {menu.open && menuSpec && (
        <FilterMenu key={menu.open.column} spec={menuSpec} left={menu.open.left} top={menu.open.top}
          onApply={(values) => { table.setFilter(menu.open!.column, values); menu.close() }} onClose={menu.close} />
      )}
      {answering && (
        <AnswerDialog key={answering.id} request={answering} closeUrl={closeUrl} onClose={closeDialog}
          counterUrl={urlWith(pathname, params, { counter: String(answering.id), answer: null })}
          error={result?.intent === 'agree' ? result : undefined} />
      )}
      {countering && (
        <CounterDialog key={countering.id} request={countering} closeUrl={closeUrl} onClose={closeDialog}
          error={result?.intent === 'counter' ? result : undefined} />
      )}
    </section>
  )
}
