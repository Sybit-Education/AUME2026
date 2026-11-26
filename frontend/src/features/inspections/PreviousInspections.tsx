import { useTranslation } from 'react-i18next'
import type { PreviousInspection } from './api'
import { formatDate } from './history'

/** Vorgeschichte der WG in der laufenden Kontrolle (US-51), aufgebaut wie „Frühere Kontrollen“ im Wireframe. */
export default function PreviousInspections({ previous }: { previous: PreviousInspection[] }) {
  const { t, i18n } = useTranslation()
  const defects = previous.reduce((sum, inspection) => sum + inspection.defects.length, 0)
  return (
    // Offen, solange es frühere Mängel gibt: Die sollen vor Ort ohne zusätzlichen Klick sichtbar sein.
    <details className="card ins-previous" open={defects > 0}>
      <summary>
        <h3>{t('inspections.previous.title')}</h3>
        <span className="xs mut">
          {previous.length === 0 ? t('inspections.previous.none') : t('inspections.previous.summary', { count: previous.length })}
        </span>
      </summary>
      {previous.map((inspection) => (
        <div className="mg" key={inspection.id}>
          <div className="ch" style={{ marginBottom: 4 }}>
            <b>
              {formatDate(inspection.inspectedAt, i18n.resolvedLanguage)} ·{' '}
              {inspection.round > 0 ? t('inspections.round', { round: inspection.round }) : t('inspections.history.initial')}
            </b>
            {inspection.overallResult && (
              <span className={`chip ${inspection.overallResult === 'PASSED' ? 'ok' : 'bad'}`}>{t(`inspections.overall.${inspection.overallResult}`)}</span>
            )}
          </div>
          {inspection.defects.length === 0 ? (
            <p className="sm2 mut">{t('inspections.previous.noDefects')}</p>
          ) : (
            <ul className="ins-previous-list">
              {inspection.defects.map((defect) => (
                <li className="sm2" key={`${defect.area}|${defect.criterionTitle}`}>
                  <b>{defect.area}, {defect.criterionTitle}</b>
                  {defect.rating === 'RESOLVED_ON_SITE'
                    ? <span className="chip ok">{t('inspections.rating.RESOLVED_ON_SITE')}</span>
                    : defect.severity && (
                      <span className={`chip ${defect.severity === 'SEVERE' ? 'bad' : 'wr'}`}>{t(`inspections.severity.${defect.severity}`)}</span>
                    )}
                  {defect.buildingCauseSuspected && <span className="chip inf">{t('inspections.buildingCause')}</span>}
                  {defect.note && <div>{defect.note}</div>}
                </li>
              ))}
            </ul>
          )}
        </div>
      ))}
    </details>
  )
}
