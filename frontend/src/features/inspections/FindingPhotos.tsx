import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { loadPhoto } from './api'

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
  const [zoom, setZoom] = useState<string | null>(null)
  if (photoIds.length === 0 && !editable) return null

  return (
    <div className="ins-photos">
      {photoIds.map((photoId, index) => (
        <PhotoThumb key={photoId} inspectionId={inspectionId} photoId={photoId}
          alt={t('inspections.photo.alt', { label, number: index + 1 })} editable={editable} busy={busy}
          onOpen={setZoom} onRemove={() => onRemove?.(photoId)} />
      ))}
      {editable && canAdd && (
        <label className="up">
          📷 {t('inspections.photo.add')}
          <input type="file" accept="image/*" capture="environment" hidden disabled={busy}
            onChange={(event) => {
              const file = event.target.files?.[0]
              event.target.value = ''
              if (file) onAdd?.(file)
            }} />
        </label>
      )}
      {zoom && (
        <div className="mbg" role="dialog" aria-modal="true" aria-label={label} onClick={() => setZoom(null)}>
          <img className="zoom" src={zoom} alt={label} />
        </div>
      )}
    </div>
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
      {editable && (
        <button type="button" className="btn gh sm" disabled={busy} onClick={onRemove}>{t('inspections.photo.remove')}</button>
      )}
    </figure>
  )
}
