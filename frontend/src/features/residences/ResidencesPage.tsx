import { useTranslation } from 'react-i18next'
import { useLoaderData, useSearchParams } from 'react-router'
import LoadingPage from '../../pages/LoadingPage'
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
    <section>
      <h2>{t('navigation.residences')}</h2>
      {data.content.length === 0 ? <p>{t('pages.noResidences')}</p> : (
        <ul>{data.content.map((residence) => <li key={residence.id}>{residence.name}</li>)}</ul>
      )}
      <nav className="pagination" aria-label={t('pages.pagination')}>
        <button disabled={data.page.number === 0} onClick={() => goToPage(data.page.number - 1)}>{t('pages.previous')}</button>
        <button disabled={data.page.number + 1 >= data.page.totalPages} onClick={() => goToPage(data.page.number + 1)}>{t('pages.next')}</button>
      </nav>
    </section>
  )
}
