import { useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useLoaderData, useNavigate, useRevalidator, useSearchParams } from 'react-router'
import LoadingPage from '@/components/LoadingPage'
import { filtersOf, formatPeriod, OVERVIEW_STATES, PERIOD_KINDS, stateChip, type OverviewData, type ResidenceRow } from './api'
import './inspection-overview.css'

const REFRESH_MS = 30_000

export default function InspectionOverviewPage() {
  const { t, i18n } = useTranslation()
  const data = useLoaderData<OverviewData | null>()
  const [params, setParams] = useSearchParams()
  const navigate = useNavigate()
  const { revalidate } = useRevalidator()

  useEffect(() => {
    const refresh = () => { if (document.visibilityState === 'visible') void revalidate() }
    const timer = window.setInterval(refresh, REFRESH_MS)
    document.addEventListener('visibilitychange', refresh)
    return () => {
      window.clearInterval(timer)
      document.removeEventListener('visibilitychange', refresh)
    }
  }, [revalidate])

  if (!data) return <LoadingPage />

  const filters = filtersOf(params)
  const { rows, counts } = data
  const setFilter = (key: 'state' | 'kind', value: string | null) => {
    const next = new URLSearchParams(params)
    next.delete('page')
    if (value) next.set(key, value)
    else next.delete(key)
    setParams(next)
  }
  const goToPage = (page: number) => {
    const next = new URLSearchParams(params)
    next.set('page', String(page))
    setParams(next)
  }
  const tiles = [
    ['COUNTER_PROPOSED', counts.counterProposed, 'inspectionOverview.counterOpen'],
    ['AGREED', counts.agreedNotAnnounced, 'inspectionOverview.agreedOpen'],
  ] as const

  return (
    <section className="card">
      <h2>{t('navigation.dashboard')}</h2>
      <div className="g4" style={{ marginBottom: 12 }} role="group" aria-label={t('inspectionOverview.openWork')}>
        {tiles.map(([state, count, label]) => (
          <button key={state} type="button" className={`kpi ${count > 0 ? 'al' : ''}`} aria-pressed={filters.state === state}
            onClick={() => setFilter('state', filters.state === state ? null : state)}>
            <span className="n">{count}</span><span className="l">{t(label)}</span>
          </button>
        ))}
      </div>
      <div className="flt">
        <label className="lbl" htmlFor="overview-state">{t('inspectionOverview.state')}</label>
        <select id="overview-state" value={filters.state ?? ''} onChange={(event) => setFilter('state', event.target.value)}>
          <option value="">{t('inspectionOverview.all')}</option>
          {OVERVIEW_STATES.map((state) => <option key={state} value={state}>{t(`inspectionOverview.states.${state}`)}</option>)}
        </select>
        <label className="lbl" htmlFor="overview-kind">{t('inspectionOverview.kind')}</label>
        <select id="overview-kind" value={filters.kind ?? ''} onChange={(event) => setFilter('kind', event.target.value)}>
          <option value="">{t('inspectionOverview.all')}</option>
          {PERIOD_KINDS.map((kind) => <option key={kind} value={kind}>{t(`inspectionOverview.kinds.${kind}`)}</option>)}
        </select>
        {(filters.state || filters.kind) && (
          <button type="button" className="btn sec" onClick={() => setParams(new URLSearchParams())}>{t('inspectionOverview.reset')}</button>
        )}
      </div>
      {rows.content.length === 0 ? <p className="mut">{t('inspectionOverview.noMatches')}</p> : (
        <div className="tw">
          <table className="tbl">
            <thead>
              <tr>
                <th>{t('inspectionOverview.residence')}</th>
                <th>{t('inspectionOverview.last')}</th>
                <th>{t('inspectionOverview.upcoming')}</th>
                <th>{t('inspectionOverview.status')}</th>
                <th>{t('inspectionOverview.nextStep')}</th>
              </tr>
            </thead>
            <tbody>
              {rows.content.map((row) => (
                <tr key={row.id} className="sel-row" onClick={() => { void navigate(`/inspection-overview/${row.id}`, { state: { name: row.name } }) }}>
                  <td><Link to={`/inspection-overview/${row.id}`} state={{ name: row.name }} onClick={(event) => event.stopPropagation()}>{row.name}</Link></td>
                  {row.state === 'NONE' ? <td colSpan={3} className="mut">{t('inspectionOverview.states.NONE')}</td> : (
                    <>
                      <td>{row.lastInspection
                        ? `${formatPeriod(row.lastInspection.on, row.lastInspection.on, i18n.language)} · ${t(`inspectionOverview.kinds.${row.lastInspection.kind}`)}`
                        : '–'}</td>
                      <td>{row.upcoming
                        ? `${formatPeriod(row.upcoming.startsOn, row.upcoming.endsOn, i18n.language)} · ${t(`inspectionOverview.kinds.${row.upcoming.kind}`)}`
                        : '–'}</td>
                      <td><span className={`chip ${stateChip[row.state]}`}>{t(`inspectionOverview.states.${row.state}`)}</span></td>
                    </>
                  )}
                  <td onClick={(event) => event.stopPropagation()}><NextStep row={row} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <p className="xs mut">{t('inspectionOverview.rowHint')}</p>
      {rows.page.totalPages > 1 && (
        <nav className="acts" aria-label={t('pages.pagination')}>
          <button className="btn sec" disabled={rows.page.number === 0} onClick={() => goToPage(rows.page.number - 1)}>{t('pages.previous')}</button>
          <button className="btn sec" disabled={rows.page.number + 1 >= rows.page.totalPages} onClick={() => goToPage(rows.page.number + 1)}>{t('pages.next')}</button>
        </nav>
      )}
    </section>
  )
}

function NextStep({ row }: { row: ResidenceRow }) {
  const { t } = useTranslation()
  switch (row.state) {
    case 'NONE':
    case 'NO_UPCOMING':
      return <Link className="btn sec" to={`/inspection-planning?residence=${row.id}`}>{t('inspectionOverview.plan')}</Link>
    case 'COUNTER_PROPOSED':
      return <Link className="btn sec" to={`/inspection-planning?review=${row.upcoming?.id}`}>{t('inspectionOverview.review')}</Link>
    case 'AGREED':
      return <button type="button" className="btn sec" disabled title={t('inspectionOverview.announceLater')}>{t('inspectionOverview.announce')}</button>
    default:
      return null
  }
}
