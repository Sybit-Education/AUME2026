import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { PreviousInspection } from './api'
import { formatDate } from './history'

/** Vorgeschichte der WG in der laufenden Kontrolle (US-51), aufgebaut wie „Frühere Kontrollen“ im Wireframe. */
export default function PreviousInspections({ previous }: { previous: PreviousInspection[] }) {
  const { t, i18n } = useTranslation()
  // Immer aufgeklappt, damit frühere Mängel vor Ort ohne Klick sichtbar sind; der Pfeil rechts klappt zu.
  const [open, setOpen] = useState(true)
  return (
    <details className="card ins-previous" open={open} onToggle={(event) => setOpen(event.currentTarget.open)}>
      <summary>
        <h3>{t('inspections.previous.title')}</h3>
        {/* Ohne frühere Kontrollen steht der Hinweis unter der Überschrift; sonst nur die Überschrift. */}
        {previous.length === 0 && <span className="xs mut ins-previous-sub">{t('inspections.previous.none')}</span>}
      </summary>
      {previous.map((inspection) => (
        // Rot umrandet nur bei „Nicht bestanden“, sonst in der Farbe der übrigen Karten.
        <div className={inspection.overallResult === 'FAILED' ? 'mg' : 'mg ins-previous-ok'} key={inspection.id}>
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
