import { useTranslation } from 'react-i18next'
import { Link, useLoaderData, useLocation, useSearchParams } from 'react-router'
import LoadingPage from '@/components/LoadingPage'
import { formatPeriod, stateChip, type loadResidenceInspections } from './api'

export default function ResidenceInspectionsPage() {
  const { t, i18n } = useTranslation()
  const data = useLoaderData<Awaited<ReturnType<typeof loadResidenceInspections>> | null>()
  const [, setParams] = useSearchParams()
  const name = (useLocation().state as { name?: string } | null)?.name
  if (!data) return <LoadingPage />

  return (
    <section className="card">
      <p className="acts"><Link className="btn sec" to="/">{t('inspectionOverview.back')}</Link></p>
      <h2>{name ? t('inspectionOverview.inspectionsOf', { name }) : t('inspectionOverview.inspections')}</h2>
      {data.content.length === 0 ? <p className="mut">{t('inspectionOverview.noInspections')}</p> : (
        <div className="tw">
          <table className="tbl">
            <thead>
              <tr>
                <th>{t('inspectionOverview.flat')}</th>
                <th>{t('inspectionOverview.kind')}</th>
                <th>{t('inspectionOverview.period')}</th>
                <th>{t('inspectionOverview.status')}</th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((row, index) => (
                <tr key={index}>
                  <td>{row.flat ? `WG ${row.flat}` : t('inspectionOverview.wholeResidence')}</td>
                  <td>{t(`inspectionOverview.kinds.${row.kind}`)}</td>
                  <td>{formatPeriod(row.startsOn, row.endsOn, i18n.language)}</td>
                  <td><span className={`chip ${stateChip[row.status]}`}>{t(`inspectionOverview.states.${row.status}`)}</span></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {data.page.totalPages > 1 && (
        <nav className="acts" aria-label={t('pages.pagination')}>
          <button className="btn sec" disabled={data.page.number === 0} onClick={() => setParams({ page: String(data.page.number - 1) })}>{t('pages.previous')}</button>
          <button className="btn sec" disabled={data.page.number + 1 >= data.page.totalPages} onClick={() => setParams({ page: String(data.page.number + 1) })}>{t('pages.next')}</button>
        </nav>
      )}
    </section>
  )
}
