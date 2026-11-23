import { useTranslation } from 'react-i18next'
import { Form, Link, useLoaderData, useSearchParams } from 'react-router'
import LoadingPage from '@/components/LoadingPage'
import type { HistoryPage as HistoryData } from './api'
import { formatDate, hasFilter, type HistoryFilter } from './history'
import InspectionTabs from './InspectionTabs'

export interface HistoryLoaderData {
  filter: HistoryFilter
  /** `null`, wenn der Zeitraum ungültig ist und deshalb nichts geladen wurde. */
  data: HistoryData | null
}

export default function HistoryPage() {
  const { t, i18n } = useTranslation()
  const loaded = useLoaderData<HistoryLoaderData | null>()
  const [params, setParams] = useSearchParams()

  if (!loaded) return <LoadingPage />
  const { filter, data } = loaded
  const date = (value: string | null) => formatDate(value, i18n.resolvedLanguage)

  function goToPage(page: number) {
    const next = new URLSearchParams(params)
    next.set('page', String(page))
    setParams(next)
  }

  return (
    <section className="card obm">
      <InspectionTabs />
      <h2>{t('inspections.history.title')}</h2>

      {/* key: nach „Zurücksetzen“ oder Zurück-Navigation zeigt das Formular wieder die Werte aus der Adresse. */}
      <Form method="get" className="ins-filter" key={params.toString()} role="search" aria-label={t('inspections.history.filter')}>
        <div className="fld">
          <label htmlFor="history-flat">{t('inspections.history.flat')}</label>
          <input id="history-flat" name="flat" maxLength={80} defaultValue={filter.flat} placeholder="S-01" />
        </div>
        <div className="fld">
          <label htmlFor="history-result">{t('inspections.result')}</label>
          <select id="history-result" name="result" defaultValue={filter.result}>
            <option value="">{t('inspections.history.all')}</option>
            <option value="PASSED">{t('inspections.overall.PASSED')}</option>
            <option value="FAILED">{t('inspections.overall.FAILED')}</option>
          </select>
        </div>
        <div className="fld">
          <label htmlFor="history-kind">{t('inspections.history.kind')}</label>
          <select id="history-kind" name="followUp" defaultValue={filter.followUp}>
            <option value="">{t('inspections.history.all')}</option>
            <option value="false">{t('inspections.history.initial')}</option>
            <option value="true">{t('inspections.history.followUp')}</option>
          </select>
        </div>
        <div className="fld">
          <label htmlFor="history-from">{t('inspections.history.from')}</label>
          <input id="history-from" name="from" type="date" defaultValue={filter.from} />
        </div>
        <div className="fld">
          <label htmlFor="history-to">{t('inspections.history.to')}</label>
          <input id="history-to" name="to" type="date" defaultValue={filter.to} />
        </div>
        <div className="acts ins-filter-acts">
          <button className="btn sm" type="submit">{t('inspections.history.apply')}</button>
          {hasFilter(filter) && <Link className="btn sm gh" to="/inspections/history">{t('inspections.history.reset')}</Link>}
        </div>
      </Form>

      {!data ? (
        <p className="err" role="alert">{t('inspections.history.invalidRange')}</p>
      ) : data.content.length === 0 ? (
        <p className="mut">{t(hasFilter(filter) ? 'inspections.history.noMatch' : 'inspections.history.empty')}</p>
      ) : (
        <div className="tw">
          <table className="tbl">
            <thead>
              <tr>
                <th>{t('inspections.history.date')}</th>
                <th>{t('inspections.history.flat')}</th>
                <th>{t('inspections.history.kind')}</th>
                <th>{t('inspections.result')}</th>
                <th>{t('inspections.defects')}</th>
                <th>{t('inspections.history.inspector')}</th>
                <th><span className="ins-sr">{t('inspections.history.view')}</span></th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((entry) => (
                <tr key={entry.id}>
                  <td>{date(entry.inspectedAt)}</td>
                  <td><b>{entry.flatLabel}</b></td>
                  <td>{entry.round > 0 ? t('inspections.round', { round: entry.round }) : t('inspections.history.initial')}</td>
                  <td>
                    {entry.overallResult
                      ? <span className={`chip ${entry.overallResult === 'PASSED' ? 'ok' : 'bad'}`}>{t(`inspections.overall.${entry.overallResult}`)}</span>
                      : '–'}
                  </td>
                  <td>{entry.deficiencies}</td>
                  <td>{entry.inspectorName ?? '–'}</td>
                  <td>
                    <Link className="btn sm gh" to={`/inspections/${entry.id}`}
                      aria-label={t('inspections.history.viewOf', { label: entry.flatLabel, date: date(entry.inspectedAt) })}>
                      {t('inspections.history.view')}
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {data && data.page.totalPages > 1 && (
        <nav className="acts" aria-label={t('pages.pagination')}>
          <button className="btn sec" disabled={data.page.number === 0} onClick={() => goToPage(data.page.number - 1)}>{t('pages.previous')}</button>
          <button className="btn sec" disabled={data.page.number + 1 >= data.page.totalPages} onClick={() => goToPage(data.page.number + 1)}>{t('pages.next')}</button>
        </nav>
      )}
    </section>
  )
}
