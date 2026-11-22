import { useTranslation } from 'react-i18next'
import { Link, useLoaderData } from 'react-router'
import { useAuth } from '@/lib/auth/AuthContext'
import LoadingPage from '@/components/LoadingPage'
import type { CaretakerOverview, OverviewState, UpcomingInspection } from './api'
import { inspectionDates } from './dates'
import './overview.css'

// Inspection list from PR #61; damage reports (#12) have no page yet, so that tile stays static.
const INSPECTIONS_PATH = '/inspections'
const INSPECTIONS_PRIVILEGE = 'INSPECTION_PERFORM'

// Not yet announced: nothing to do here until planning has finished (proposals without canRespond are a deputy's).
const WAITING = ['PROPOSED', 'COUNTER_PROPOSED', 'AGREED'] as const

function isWaiting(state: OverviewState): state is typeof WAITING[number] {
  return (WAITING as readonly OverviewState[]).includes(state)
}

const stateChip: Record<OverviewState, string> = {
  PROPOSED: 'chip wr',
  COUNTER_PROPOSED: 'chip wr',
  AGREED: 'chip ok',
  ANNOUNCED: 'chip t',
  PLANNED: 'chip',
  IN_PROGRESS: 'chip inf',
}

export default function OverviewPage() {
  const { t } = useTranslation()
  const { user } = useAuth()
  const data = useLoaderData<CaretakerOverview | null>()
  if (!data) return <LoadingPage />
  const canOpenInspections = user?.privileges.includes(INSPECTIONS_PRIVILEGE) ?? false

  const tiles = [
    { key: 'proposals', value: data.proposals, urgent: data.proposals > 0, to: INSPECTIONS_PATH },
    { key: 'announced', value: data.announced, urgent: false, to: INSPECTIONS_PATH },
    { key: 'followUps', value: data.followUps, urgent: false, to: INSPECTIONS_PATH },
    { key: 'openDamages', value: data.openDamages, urgent: false, to: null },
  ] as const

  return (
    <>
      <h2 className="overview-title">{t('overview.hello', { name: user?.name })}</h2>
      <div className="g4 overview-tiles">
        {tiles.map((tile) => {
          const content = (
            <>
              <div className={tile.urgent ? 'overview-count urgent' : 'overview-count'}>{tile.value ?? '–'}</div>
              <div className="mut sm2">{t(`overview.tiles.${tile.key}`)}</div>
              {tile.value === null && <div className="xs mut">{t('overview.notAvailable')}</div>}
            </>
          )
          return tile.to && canOpenInspections
            ? <Link className="card overview-tile-link" key={tile.key} to={tile.to}>{content}</Link>
            : <section className="card" key={tile.key}>{content}</section>
        })}
      </div>
      <section className="card">
        <h3>{t('overview.upcoming')}</h3>
        {data.upcoming.length === 0 ? <p className="mut">{t('overview.none')}</p> : (
          <div className="overview-table-wrap">
            <table className="overview-table">
              <thead>
                <tr>
                  <th>{t('overview.columns.flat')}</th>
                  <th>{t('overview.columns.kind')}</th>
                  <th>{t('overview.columns.date')}</th>
                  <th>{t('overview.columns.state')}</th>
                  <th><span className="sr-only">{t('overview.columns.action')}</span></th>
                </tr>
              </thead>
              <tbody>{data.upcoming.map((inspection) => <Row key={inspection.id} inspection={inspection} />)}</tbody>
            </table>
          </div>
        )}
      </section>
    </>
  )
}

function Row({ inspection }: { inspection: UpcomingInspection }) {
  const { t, i18n } = useTranslation()
  const dates = inspectionDates(inspection, i18n.resolvedLanguage ?? 'de')
  return (
    <tr>
      <td>
        {/* The floor shown in the mockup is missing from the flat data model (flats); to be clarified with US-02 (#30). */}
        <div>{inspection.flatLabel}</div>
        <div className="xs mut">{inspection.residenceName}</div>
      </td>
      <td>{t(`overview.kind.${inspection.kind}`, { round: inspection.round })}</td>
      <td>
        {/* Time window and day per flat from the mockup are still missing; they come with scheduling (US-17, #35). */}
        {dates?.type === 'period' && t('overview.period', { from: dates.from, to: dates.to })}
        {dates?.type === 'due' && t('overview.dueBy', { date: dates.date })}
        {!dates && <span className="mut">{t('overview.noDate')}</span>}
      </td>
      <td><span className={stateChip[inspection.state]}>{t(`overview.state.${inspection.state}`)}</span></td>
      <td className="overview-action">
        {inspection.canRespond ? (
          // Missing: no page on develop for answering proposals yet (#44). The API exists in inspection planning.
          <>
            <button className="btn sec sm" disabled>{t('overview.answer')}</button>
            <div className="xs mut">{t('overview.missing')}</div>
          </>
        ) : isWaiting(inspection.state) ? (
          <span className="xs mut">{t(`overview.waiting.${inspection.state}`)}</span>
        ) : (
          // Target /inspections/:id is the inspection page from PR #61.
          <Link className="btn sm" to={`/inspections/${inspection.id}`}>
            {inspection.state === 'IN_PROGRESS' ? t('overview.continue') : t('overview.start')}
          </Link>
        )}
      </td>
    </tr>
  )
}
