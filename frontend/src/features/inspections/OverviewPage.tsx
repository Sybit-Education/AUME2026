import { useTranslation } from 'react-i18next'
import { Link, useLoaderData } from 'react-router'
import { useAuth } from '@/lib/auth/AuthContext'
import LoadingPage from '@/components/LoadingPage'
import type { CurrentInspections } from './currentInspections'
import RequestsSection from './RequestsSection'
import UpcomingSection from './UpcomingSection'
import { statusOf } from './upcomingStatus'
import { useToday } from './useToday'
import './overview.css'

const INSPECTIONS_PATH = '/inspections'

/** Kennzahlen und darunter dieselben Tabellen wie auf der Seite Kontrollen (Anfragen, anstehende Kontrollen). */
export default function OverviewPage() {
  const { t } = useTranslation()
  const { user } = useAuth()
  const day = useToday()
  const data = useLoaderData<CurrentInspections | null>()
  if (!data) return <LoadingPage />

  const inspections = data.inspections.content
  const proposals = data.requests?.content.filter((request) => request.status === 'WAITING_FOR_CARETAKER').length
  const tiles = [
    { key: 'proposals', value: proposals },
    // Wie „Angekündigt“ in der Tabelle: angekündigt, aber der Zeitraum hat noch nicht begonnen.
    { key: 'announced', value: inspections.filter((inspection) => statusOf(inspection, day) === 'ANNOUNCED').length },
    { key: 'followUps', value: inspections.filter((inspection) => inspection.round > 0).length },
  ] as const

  return (
    <>
      <h2 className="overview-title">{t('overview.hello', { name: user?.name })}</h2>
      <div className="g3 overview-tiles">
        {tiles.map((tile) => tile.value !== undefined && (
          <Link className="card overview-tile-link" key={tile.key} to={INSPECTIONS_PATH}>
            <div className={tile.key === 'proposals' && tile.value > 0 ? 'overview-count urgent' : 'overview-count'}>{tile.value}</div>
            <div className="mut sm2">{t(`overview.tiles.${tile.key}`)}</div>
          </Link>
        ))}
      </div>
      <section className="card obm">
        {data.requests && <RequestsSection data={data.requests} />}
        <UpcomingSection inspections={inspections} />
      </section>
    </>
  )
}
