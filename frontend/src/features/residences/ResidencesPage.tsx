import { useTranslation } from 'react-i18next'
import { useLoaderData, useSearchParams } from 'react-router'
import LoadingPage from '@/components/LoadingPage'
import type { ResidencePage } from './api'

export default function ResidencesPage() {
  const { t } = useTranslation()
  const data = useLoaderData<ResidencePage | null>()
  const [params, setParams] = useSearchParams()

  function goToPage(page: number) {
    const next = new URLSearchParams(params)
    next.set('page', String(page))
    setParams(next)
  }

  if (!data) return <LoadingPage />

  return (
    <section className="card">
      <h2>{t('navigation.residences')}</h2>
      {data.content.length === 0 ? <p className="mut">{t('pages.noResidences')}</p> : (
        <div>{data.content.map((residence) => <div className="row" key={residence.id}><div>{residence.name}</div></div>)}</div>
      )}
      <nav className="acts" aria-label={t('pages.pagination')}>
        <button className="btn sec" disabled={data.page.number === 0} onClick={() => goToPage(data.page.number - 1)}>{t('pages.previous')}</button>
        <button className="btn sec" disabled={data.page.number + 1 >= data.page.totalPages} onClick={() => goToPage(data.page.number + 1)}>{t('pages.next')}</button>
      </nav>
    </section>
  )
}
