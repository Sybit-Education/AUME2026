import { useTranslation } from 'react-i18next'
import type { Finding } from './api'
import { areaIcon, type AreaState } from './summary'

/** Protokoll einer abgeschlossenen Kontrolle: je Bereich die Mängel mit Schwere und Notiz (nur lesend). */
export default function Protocol({ states, findings }: { states: AreaState[]; findings: Finding[] }) {
  const { t } = useTranslation()
  const byCriterion = new Map(findings.map((finding) => [finding.criterionId, finding]))
  return (
    <section className="card">
      <h3>{t('inspections.protocol.title')}</h3>
      {states.map((state) => {
        const items = state.criteria.flatMap((criterion) => {
          const finding = byCriterion.get(criterion.id)
          return finding && finding.rating !== 'OK' ? [{ criterion, finding }] : []
        })
        return (
          <div className="mg" key={state.area}>
            <div className="ch" style={{ marginBottom: 4 }}>
              <b>{areaIcon(state.area)} {state.area}</b>
              {state.deficient > 0
                ? <span className="chip bad">{t('inspections.deficiencies', { count: state.deficient })}</span>
                : <span className="chip ok">{t('inspections.inOrder')}</span>}
            </div>
            {items.map(({ criterion, finding }) => (
              <div className="sm2 protocol-item" key={criterion.id}>
                <b>{criterion.title}</b>
                {finding.rating === 'RESOLVED_ON_SITE'
                  ? <span className="chip ok">{t('inspections.rating.RESOLVED_ON_SITE')}</span>
                  : finding.severity && (
                    <span className={`chip ${finding.severity === 'SEVERE' ? 'bad' : 'wr'}`}>{t(`inspections.severity.${finding.severity}`)}</span>
                  )}
                {finding.buildingCauseSuspected && <span className="chip inf">{t('inspections.buildingCause')}</span>}
                {finding.note && <div>{finding.note}</div>}
              </div>
            ))}
          </div>
        )
      })}
    </section>
  )
}
