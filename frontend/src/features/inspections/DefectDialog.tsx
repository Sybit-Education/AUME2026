import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { Criterion, Finding, Severity } from './api'
import Select from '@/components/Select'

interface DefectDialogProps {
  area: string
  criteria: Criterion[]
  findings: Finding[]
  busy: boolean
  onCancel: () => void
  /** {@code photo}: optionales Foto zum Mangel (US-35), wird nach dem Speichern hochgeladen. */
  onSave: (criterionId: number, severity: Severity, note: string, photo?: File) => void
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
  const [photo, setPhoto] = useState<{ file: File; url: string } | null>(null)
  const [photoError, setPhotoError] = useState(false)

  // Vorschau-URL wieder freigeben, wenn ein anderes Foto gewählt oder der Dialog geschlossen wird.
  useEffect(() => () => { if (photo) URL.revokeObjectURL(photo.url) }, [photo])

  function choosePhoto(file?: File) {
    if (!file) return
    if (!file.type.startsWith('image/')) {
      setPhotoError(true)
      return
    }
    setPhotoError(false)
    setPhoto({ file, url: URL.createObjectURL(file) })
  }

  function save() {
    if (!criterionId || !note.trim()) {
      setMissing(true)
      return
    }
    onSave(criterionId, severity, note.trim(), photo?.file)
  }

  return (
    <div className="mbg" onClick={(event) => { if (event.target === event.currentTarget && !busy) onCancel() }}>
      <div className="mdl" role="dialog" aria-modal="true" aria-labelledby="defect-title">
        <h3 id="defect-title">{t('inspections.defectTitle', { area })}</h3>
        <div className="fld">
          <label htmlFor="defect-criterion">{t('inspections.criterion')} *</label>
          <Select id="defect-criterion" value={String(criterionId)} onChange={(value) => setCriterionId(Number(value))}
            options={criteria.map((criterion) => ({
              value: String(criterion.id),
              label: defectOf(criterion.id) ? `${criterion.title} (${t('inspections.hasDefect')})` : criterion.title,
            }))} />
          {existing && <p className="note wr" role="status">{t('inspections.replacesDefect')}</p>}
        </div>
        <div className="fld">
          <label htmlFor="defect-severity">{t('inspections.severityLabel')}</label>
          <Select id="defect-severity" value={severity} onChange={(value) => setSeverity(value as Severity)} options={[
            { value: 'LIGHT', label: t('inspections.severity.LIGHT') },
            { value: 'SEVERE', label: t('inspections.severity.SEVERE') },
          ]} />
        </div>
        <div className="fld">
          <label htmlFor="defect-note">{t('inspections.note')} *</label>
          <textarea id="defect-note" maxLength={2000} value={note} autoFocus
            onChange={(event) => { setNote(event.target.value); setMissing(false) }} />
          {missing && <p className="err" role="alert">{t('inspections.noteRequired')}</p>}
        </div>
        <div className="fld">
          <span className="lbl" id="defect-photo-label">{t('inspections.photo.optional')}</span>
          <label className="up" aria-labelledby="defect-photo-label">
            📷 {t('inspections.photo.choose')}
            <input type="file" accept="image/*" capture="environment" hidden disabled={busy}
              onChange={(event) => { choosePhoto(event.target.files?.[0]); event.target.value = '' }} />
          </label>
          {photo && (
            <div className="ins-photo-preview">
              <img className="thumb" src={photo.url} alt={t('inspections.photo.selected', { name: photo.file.name })} />
              <button type="button" className="btn gh sm" disabled={busy} onClick={() => setPhoto(null)}>{t('inspections.photo.remove')}</button>
            </div>
          )}
          {photoError && <p className="err" role="alert">{t('errors.photoType')}</p>}
        </div>
        <div className="ft">
          <button className="btn gh" disabled={busy} onClick={onCancel}>{t('inspections.cancel')}</button>
          <button className="btn" disabled={busy} onClick={save}>{t('inspections.saveDefect')}</button>
        </div>
      </div>
    </div>
  )
}
