import { useTranslation } from 'react-i18next'
import { useLoaderData } from 'react-router'
import LoadingPage from '@/components/LoadingPage'
import type { CurrentInspections } from './currentInspections'
import RequestsSection from './RequestsSection'
import UpcomingSection from './UpcomingSection'
import './inspections.css'

// Seite Kontrollen der Objektbetreuung: oben die Anfragen (Zeitraum abstimmen), darunter die anstehenden Kontrollen.

export default function InspectionsPage() {
  const { t } = useTranslation()
  const loaded = useLoaderData<CurrentInspections | null>()

  if (!loaded) return <LoadingPage />

  return (
    <section className="card obm">
      <h2>{t('navigation.inspections')}</h2>
      {loaded.requests && <RequestsSection data={loaded.requests} />}
      <UpcomingSection inspections={loaded.inspections.content} />
    </section>
  )
}
