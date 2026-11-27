import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { loadPhoto, PHOTO_TYPES } from './api'

interface FindingPhotosProps {
  inspectionId: number
  photoIds: number[]
  /** Beschreibender Alternativtext, z. B. „Foto zum Mangel Herd & Backofen“. */
  label: string
  editable?: boolean
  busy?: boolean
  canAdd?: boolean
  onAdd?: (file: File) => void
  onRemove?: (photoId: number) => void
}

/** Vorschaubilder der Fotos eines Mangels; im Entwurf mit „Foto hinzufügen“ und Entfernen (US-35). */
export default function FindingPhotos({ inspectionId, photoIds, label, editable, busy, canAdd, onAdd, onRemove }: FindingPhotosProps) {
  const { t } = useTranslation()
  const [zoom, setZoom] = useState<{ photoId: number; url: string } | null>(null)
  // Die URL gehört dem Vorschaubild und wird mit ihm freigegeben: Verschwindet das Foto, schließt auch die Vergrößerung.
  const zoomed = zoom && photoIds.includes(zoom.photoId) ? zoom : null
  if (photoIds.length === 0 && !editable) return null

  return (
    <div className="ins-photos">
      {photoIds.map((photoId, index) => (
        <PhotoThumb key={photoId} inspectionId={inspectionId} photoId={photoId}
          alt={t('inspections.photo.alt', { label, number: index + 1 })} editable={editable} busy={busy}
          onOpen={(url) => setZoom({ photoId, url })} onRemove={() => onRemove?.(photoId)} />
      ))}
      {editable && canAdd && (
        <AddPhotoTile label={t('inspections.photo.add')} disabled={busy} onChoose={(file) => onAdd?.(file)} />
      )}
      {zoomed && (
        <div className="mbg" role="dialog" aria-modal="true" aria-label={label} onClick={() => setZoom(null)}>
          <img className="zoom" src={zoomed.url} alt={label} />
        </div>
      )}
    </div>
  )
}

/** Kachel mit Plus in der Größe eines Vorschaubilds; öffnet Kamera oder Dateiauswahl. */
export function AddPhotoTile({ label, disabled, onChoose }: { label: string; disabled?: boolean; onChoose: (file: File) => void }) {
  return (
    <label className={disabled ? 'ins-photo-add disabled' : 'ins-photo-add'} title={label}>
      <svg viewBox="0 0 24 24" width="26" height="26" aria-hidden="true"><path d="M12 5v14M5 12h14" /></svg>
      <span className="sr-only">{label}</span>
      <input type="file" accept={PHOTO_TYPES.join(',')} capture="environment" hidden disabled={disabled}
        onChange={(event) => {
          const file = event.target.files?.[0]
          event.target.value = ''
          if (file) onChoose(file)
        }} />
    </label>
  )
}

/** Kleiner Mülleimer oben rechts auf einem Vorschaubild; beim Darüberfahren rot. */
export function PhotoRemoveButton({ label, disabled, onClick }: { label: string; disabled?: boolean; onClick: () => void }) {
  return (
    <button type="button" className="ins-photo-remove" disabled={disabled} onClick={onClick} aria-label={label} title={label}>
      <svg viewBox="0 0 24 24" width="15" height="15" aria-hidden="true">
        <path d="M4 7h16M9 7V4.5h6V7M6.5 7l1 12.5h9l1-12.5M10 11v5M14 11v5" />
      </svg>
    </button>
  )
}

interface PhotoThumbProps {
  inspectionId: number
  photoId: number
  alt: string
  editable?: boolean
  busy?: boolean
  onOpen: (url: string) => void
  onRemove: () => void
}

function PhotoThumb({ inspectionId, photoId, alt, editable, busy, onOpen, onRemove }: PhotoThumbProps) {
  const { t } = useTranslation()
  const [url, setUrl] = useState<string | null>(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    const controller = new AbortController()
    let created: string | null = null
    loadPhoto(inspectionId, photoId, controller.signal)
      .then((value) => {
        // Schon wieder ausgeblendet, während das Bild lud: URL sofort freigeben, sonst bleibt sie im Speicher.
        if (controller.signal.aborted) {
          URL.revokeObjectURL(value)
          return
        }
        created = value
        setUrl(value)
      })
      .catch(() => {
        if (!controller.signal.aborted) setFailed(true)
      })
    return () => {
      controller.abort()
      if (created) URL.revokeObjectURL(created)
    }
  }, [inspectionId, photoId])

  return (
    <figure className="ins-photo">
      <button type="button" className="ins-photo-open" disabled={!url} onClick={() => url && onOpen(url)}
        aria-label={t('inspections.photo.open', { alt })}>
        {url
          ? <img className="thumb" src={url} alt={alt} />
          : <span className="thumb ins-photo-empty">{failed ? t('inspections.photo.unavailable') : '…'}</span>}
      </button>
      {editable && <PhotoRemoveButton label={t('inspections.photo.remove')} disabled={busy} onClick={onRemove} />}
    </figure>
  )
}
