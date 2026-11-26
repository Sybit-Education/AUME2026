import { useTranslation } from 'react-i18next'
import { Link, useLoaderData } from 'react-router'
import LoadingPage from '@/components/LoadingPage'
import { formatPeriod, formatResult, formatRoundState, stateChip, type ResidenceDetail } from './api'

export default function ResidenceInspectionsPage() {
  const { t, i18n } = useTranslation()
  const data = useLoaderData<ResidenceDetail | null>()
  if (!data) return <LoadingPage />

  return (
    <>
      <p className="acts"><Link className="btn sec" to="/">{t('inspectionOverview.back')}</Link></p>
      <section className="card">
        <h2>{t('inspectionOverview.roundsOf', { name: data.name })}</h2>
        {data.rounds.length === 0 ? <p className="mut">{t('inspectionOverview.noRounds')}</p> : (
          <div className="tw">
            <table className="tbl">
              <thead>
                <tr>
                  <th>{t('inspectionOverview.period')}</th>
                  <th>{t('inspectionOverview.kind')}</th>
                  <th>{t('inspectionOverview.status')}</th>
                  <th>{t('inspectionOverview.result')}</th>
                </tr>
              </thead>
              <tbody>
                {data.rounds.map((round) => (
                  <tr key={round.id}>
                    <td>{formatPeriod(round.startsOn, round.endsOn, i18n.language)}</td>
                    <td>{t(`inspectionOverview.kinds.${round.kind}`)}</td>
                    <td><span className={`chip ${stateChip[round.state]}`}>{formatRoundState(round, t)}</span></td>
                    <td>{round.state === 'COMPLETED' || round.controlled > 0 ? formatResult(round, t) : '–'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
      <section className="card">
        <h3>{t('inspectionOverview.openSingles')}{data.openInspections.length > 0 ? ` · ${data.openInspections.length}` : ''}</h3>
        {data.openInspections.length === 0 ? <p className="mut">{t('inspectionOverview.noOpenSingles')}</p> : (
          <div className="tw">
            <table className="tbl">
              <thead>
                <tr>
                  <th>{t('inspectionOverview.flat')}</th>
                  <th>{t('inspectionOverview.kind')}</th>
                  <th>{t('inspectionOverview.period')}</th>
                  <th>{t('inspectionOverview.result')}</th>
                </tr>
              </thead>
              <tbody>
                {data.openInspections.map((row) => (
                  <tr key={`${row.flat}-${row.kind}-${row.startsOn}`}>
                    <td>WG {row.flat}</td>
                    <td>{t(`inspectionOverview.kinds.${row.kind}`)}</td>
                    <td>{formatPeriod(row.startsOn, row.endsOn, i18n.language)}</td>
                    <td><span className={`chip ${stateChip[row.outcome]}`}>{t(`inspectionOverview.outcomes.${row.outcome}`)}</span></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <p className="xs mut">{t('inspectionOverview.openSinglesHint')}</p>
      </section>
    </>
  )
}
