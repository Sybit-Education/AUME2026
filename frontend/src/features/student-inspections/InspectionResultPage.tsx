import { useTranslation } from 'react-i18next'
import { useLoaderData } from 'react-router'
import LoadingPage from '@/components/LoadingPage'
import type { StudentResult } from './api'

export type InspectionResultData = { result: StudentResult | null; announced: boolean }

/**
 * US-38: Ergebnis und Mängel der eigenen WG (Reiter „Ergebnis“). Nur Anzeige; Rückmeldungen wie
 * „Als erledigt melden“ kommen erst in Release 2.
 */
export default function InspectionResultPage() {
  const { t } = useTranslation()
  const data = useLoaderData<InspectionResultData | null>()
  if (!data) return <LoadingPage />
  if (!data.result) {
    return (
      <section className="card empty result-empty">
        <h4>{t('studentResult.emptyTitle')}</h4>
        <p className="mut">{t(data.announced ? 'studentResult.emptyAnnounced' : 'studentResult.emptyNext')}</p>
      </section>
    )
  }
  return <ResultView result={data.result} />
}

function ResultView({ result }: { result: StudentResult }) {
  const { t, i18n } = useTranslation()
  const failed = result.overallResult === 'FAILED'
  const date = result.inspectedAt
    ? new Intl.DateTimeFormat(i18n.resolvedLanguage, { dateStyle: 'medium', timeZone: 'Europe/Berlin' })
      .format(new Date(result.inspectedAt))
    : null
  const kind = t(result.kind === 'INITIAL' ? 'studentResult.initial' : 'studentResult.followUp')

  return (
    <>
      <section className="card result-summary" aria-labelledby="result-title">
        <h3 id="result-title">
          {date ? t('studentResult.title', { kind, date }) : t('studentResult.titleWithoutDate', { kind })}
        </h3>
        <span className="chip result-chip ok">{t('studentResult.available')}</span>
        {failed
          ? <p className="result-failed">{t('studentResult.failed')}</p>
          : <p className="note result-passed">{t('studentResult.passed')}</p>}
        <p className="mut xs">{t('studentResult.assessedBy')}</p>
      </section>

      {failed && (
        <section className="card" aria-labelledby="result-next">
          <h3 id="result-next">{t('studentResult.nextStepTitle')}</h3>
          <p>{t('studentResult.nextStep')}</p>
        </section>
      )}

      <section className="card result-list" aria-labelledby="result-deficiencies">
        <h3 id="result-deficiencies">{t('studentResult.deficienciesTitle', { count: result.deficiencies.length })}</h3>
        {result.deficiencies.length === 0 ? <p className="mut">{t('studentResult.noDeficiencies')}</p> : (
          <ul>
            {result.deficiencies.map((deficiency) => (
              <li key={`${deficiency.area}-${deficiency.criterion}`}>
                <div className="result-row">
                  <b>{deficiency.area} · {deficiency.criterion}</b>
                  {deficiency.buildingCauseSuspected
                    ? <span className="chip result-chip building">{t('studentResult.buildingCause')}</span>
                    : <span className="chip result-chip deficient">{t('studentResult.deficient')}</span>}
                </div>
                <p className="result-note">{deficiency.note?.trim() ? deficiency.note : t('studentResult.noNote')}</p>
                {deficiency.buildingCauseSuspected && <p className="mut xs">{t('studentResult.buildingCauseHint')}</p>}
              </li>
            ))}
          </ul>
        )}
      </section>

      {result.resolvedOnSite.length > 0 && (
        <section className="card result-list" aria-labelledby="result-resolved">
          <h3 id="result-resolved">{t('studentResult.resolvedTitle', { count: result.resolvedOnSite.length })}</h3>
          <ul>
            {result.resolvedOnSite.map((item) => (
              <li key={`${item.area}-${item.criterion}`} className="result-row">
                <span>{item.area} · {item.criterion}</span>
                <span className="chip result-chip ok">{t('studentResult.resolved')}</span>
              </li>
            ))}
          </ul>
        </section>
      )}

      <section className="card result-list" aria-labelledby="result-passed">
        <h3 id="result-passed">{t('studentResult.passedTitle', { count: result.passed.length })}</h3>
        <ul>
          {result.passed.map((item) => (
            <li key={`${item.area}-${item.criterion}`} className="result-row">
              <span>{item.criterion}</span>
              <span className="chip result-chip ok">{t('studentResult.ok')}</span>
            </li>
          ))}
        </ul>
      </section>
    </>
  )
}
