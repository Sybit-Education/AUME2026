import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { Criterion, Finding, Severity } from './api'

interface DefectDialogProps {
  area: string
  criteria: Criterion[]
  findings: Finding[]
  busy: boolean
  onCancel: () => void
  onSave: (criterionId: number, severity: Severity, note: string) => void
}

export default function DefectDialog({ area, criteria, findings, busy, onCancel, onSave }: DefectDialogProps) {
  const { t } = useTranslation()
  const defectOf = (id: number | undefined) =>
    findings.find((entry) => entry.criterionId === id && entry.rating !== 'OK')
  // Vorauswahl auf ein Kriterium ohne Mangel, damit ein bestehender Mangel nicht versehentlich ersetzt wird.
  const [criterionId, setCriterionId] = useState((criteria.find((criterion) => !defectOf(criterion.id)) ?? criteria[0])?.id)
  const existing = defectOf(criterionId)
  const [severity, setSeverity] = useState<Severity>('LIGHT')
  const [note, setNote] = useState('')
  const [missing, setMissing] = useState(false)

  function save() {
    if (!criterionId || !note.trim()) {
      setMissing(true)
      return
    }
    onSave(criterionId, severity, note.trim())
  }

  return (
    <div className="mbg" onClick={(event) => { if (event.target === event.currentTarget && !busy) onCancel() }}>
      <div className="mdl" role="dialog" aria-modal="true" aria-labelledby="defect-title">
        <h3 id="defect-title">{t('inspections.defectTitle', { area })}</h3>
        <div className="fld">
          <label htmlFor="defect-criterion">{t('inspections.criterion')} *</label>
          <select id="defect-criterion" value={criterionId} onChange={(event) => setCriterionId(Number(event.target.value))}>
            {criteria.map((criterion) => (
              <option key={criterion.id} value={criterion.id}>
                {defectOf(criterion.id) ? `${criterion.title} (${t('inspections.hasDefect')})` : criterion.title}
              </option>
            ))}
          </select>
          {existing && <p className="note wr" role="status">{t('inspections.replacesDefect')}</p>}
        </div>
        <div className="fld">
          <label htmlFor="defect-severity">{t('inspections.severityLabel')}</label>
          <select id="defect-severity" value={severity} onChange={(event) => setSeverity(event.target.value as Severity)}>
            <option value="LIGHT">{t('inspections.severity.LIGHT')}</option>
            <option value="SEVERE">{t('inspections.severity.SEVERE')}</option>
          </select>
        </div>
        <div className="fld">
          <label htmlFor="defect-note">{t('inspections.note')} *</label>
          <textarea id="defect-note" maxLength={2000} value={note} autoFocus
            onChange={(event) => { setNote(event.target.value); setMissing(false) }} />
          {missing && <p className="err" role="alert">{t('inspections.noteRequired')}</p>}
        </div>
        <div className="ft">
          <button className="btn gh" disabled={busy} onClick={onCancel}>{t('inspections.cancel')}</button>
          <button className="btn" disabled={busy} onClick={save}>{t('inspections.saveDefect')}</button>
        </div>
      </div>
    </div>
  )
}
