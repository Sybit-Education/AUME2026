import { useTranslation } from 'react-i18next'
import { Link, useLoaderData, useSearchParams } from 'react-router'
import LoadingPage from '@/components/LoadingPage'
import type { InspectionPage } from './api'

export default function InspectionsPage() {
  const { t } = useTranslation()
  const data = useLoaderData<InspectionPage | null>()
  const [params, setParams] = useSearchParams()

  function goToPage(page: number) {
    const next = new URLSearchParams(params)
    next.set('page', String(page))
    setParams(next)
  }

  if (!data) return <LoadingPage />

  return (
    <section className="card obm">
      <h2>{t('navigation.inspections')}</h2>
      {data.content.length === 0 ? <p className="mut">{t('inspections.none')}</p> : (
        <div>
          {data.content.map((inspection) => (
            <div className="row" key={inspection.id}>
              <div>
                <b>{t('inspections.flat', { label: inspection.flatLabel })}</b>{' '}
                <span className="chip">{t(`inspections.status.${inspection.status}`)}</span>
                {inspection.round > 0 && <> <span className="chip wr">{t('inspections.round', { round: inspection.round })}</span></>}
              </div>
              <Link className="btn sm" to={`/inspections/${inspection.id}`}>
                {inspection.status === 'PLANNED' ? t('inspections.start') : t('inspections.continue')}
              </Link>
            </div>
          ))}
        </div>
      )}
      {data.page.totalPages > 1 && (
        <nav className="acts" aria-label={t('pages.pagination')}>
          <button className="btn sec" disabled={data.page.number === 0} onClick={() => goToPage(data.page.number - 1)}>{t('pages.previous')}</button>
          <button className="btn sec" disabled={data.page.number + 1 >= data.page.totalPages} onClick={() => goToPage(data.page.number + 1)}>{t('pages.next')}</button>
        </nav>
      )}
    </section>
  )
}
