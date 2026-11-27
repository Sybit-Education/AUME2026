import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useLoaderData, useLocation, useNavigate } from 'react-router'
import LoadingPage from '@/components/LoadingPage'
import Select from '@/components/Select'
import { SessionExpired } from '@/lib/auth/response'
import { loginDestination } from '@/lib/auth/returnTo'
import { forgetUser } from '@/lib/auth/session'
import { errorKey, UiError, type ErrorKey } from '@/lib/errors'
import {
  addPhoto, clearFinding, downloadProtocol, finalizeInspection, getInspection, markOk, MAX_PHOTOS, rateCriterion,
  removePhoto, startInspection,
  type Criterion, type Finding, type Inspection, type OverallResult, type PreviousInspection, type Severity,
} from './api'
import { areaIcon, areas, suggestedResult, type AreaState } from './summary'
import DefectDialog from './DefectDialog'
import FindingPhotos from './FindingPhotos'
import { formatDate } from './history'
import Protocol from './Protocol'
import { criterionKey, earlierDefects, type EarlierDefect } from './previous'
import PreviousInspections from './PreviousInspections'
import './inspections.css'

export interface InspectionLoaderData {
  inspection: Inspection
  /** Vorgeschichte der WG (US-51), neueste zuerst. */
  previous: PreviousInspection[]
  /** Warum der direkte Start aus der Liste nicht geklappt hat. */
  startError?: ErrorKey
}

export default function InspectionPage() {
  const loaded = useLoaderData<InspectionLoaderData | null>()
  if (!loaded) return <LoadingPage />
  return <InspectionView key={loaded.inspection.id} initial={loaded.inspection} previous={loaded.previous} startError={loaded.startError} />
}

function InspectionView({ initial, previous, startError }: {
  initial: Inspection
  previous: PreviousInspection[]
  startError?: ErrorKey
}) {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const location = useLocation()
  const [inspection, setInspection] = useState(initial)
  const [step, setStep] = useState<'check' | 'summary'>(initial.status === 'DRAFT' || initial.status === 'PLANNED' ? 'check' : 'summary')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<ErrorKey | null>(startError ?? null)
  const [toast, setToast] = useState<string | null>(null)
  const [dialogOpen, setDialogOpen] = useState(false)

  const states = areas(inspection.criteria, inspection.findings)
  const [selected, setSelected] = useState(states[0]?.area)
  const current = states.find((state) => state.area === selected) ?? states[0]
  const editable = inspection.status === 'DRAFT'
  const done = inspection.status === 'FINALIZED' || inspection.status === 'PUBLISHED'
  const earlier = earlierDefects(previous)
  const open = states.reduce((sum, state) => sum + state.open, 0)

  useEffect(() => {
    if (!toast) return
    const timer = window.setTimeout(() => setToast(null), 2500)
    return () => window.clearTimeout(timer)
  }, [toast])

  async function run(action: () => Promise<Inspection>, message?: string) {
    setBusy(true)
    setError(null)
    try {
      setInspection(await action())
      if (message) setToast(message)
      return true
    } catch (cause) {
      if (cause instanceof SessionExpired || (cause instanceof UiError && cause.key === 'errors.sessionExpired')) {
        forgetUser()
        navigate(loginDestination(location.pathname + location.search), { replace: true })
        return false
      }
      // requireOk wirft bei 403 eine Redirect-Response (z. B. auf /forbidden).
      if (cause instanceof Response) {
        navigate(cause.headers.get('Location') ?? '/forbidden', { replace: true })
        return false
      }
      setError(errorKey(cause, 'errors.inspectionSave'))
      await reload()
      return false
    } finally {
      setBusy(false)
    }
  }

  /** Holt nach einem Fehler den aktuellen Stand, damit die Seite nicht auf veraltetem Status weiterarbeitet. */
  async function reload() {
    try {
      setInspection(await getInspection(inspection.id, new AbortController().signal))
    } catch {
      // Der ursprüngliche Fehler wird bereits angezeigt.
    }
  }

  const finding = (criterionId: number) => inspection.findings.find((entry) => entry.criterionId === criterionId)

  return (
    <div className="obm">
      <div className="ins-head">
        <h2>{t(step === 'summary' ? 'inspections.summaryTitle' : 'inspections.title', { label: inspection.flatLabel })}</h2>
        <div className="acts" style={{ marginTop: 0 }}>
          {inspection.status === 'PLANNED' && inspection.criteria.length > 0 && (
            <button className="btn" disabled={busy} onClick={() => void run(() => startInspection(inspection.id), t('inspections.started'))}>{t('inspections.start')}</button>
          )}
          {editable && step === 'check' && (
            <>
              <button className="btn sec" disabled={busy || open === 0} onClick={() => void run(() => markOk(inspection.id), t('inspections.allOkDone'))}>{t('inspections.allOk')}</button>
              <button className="btn" disabled={busy} onClick={() => setStep('summary')}>{t('inspections.toSummary')}</button>
            </>
          )}
          {editable && step === 'summary' && (
            <button className="btn gh" disabled={busy} onClick={() => setStep('check')}>{t('inspections.backToChecklist')}</button>
          )}
          {done && <Link className="btn gh" to="/inspections/history">{t('inspections.protocol.backToHistory')}</Link>}
          {done && (
            <button className="btn" disabled={busy}
              onClick={() => void run(async () => { await downloadProtocol(inspection.id); return inspection })}>
              {t(busy ? 'inspections.protocol.downloading' : 'inspections.protocol.download')}
            </button>
          )}
        </div>
      </div>
      <div className="ins-meta">
        <span>{inspection.residenceName}</span>
        {/* „In Bearbeitung“ weiß, wer die Kontrolle gerade bearbeitet; die übrigen Zustände bleiben sichtbar. */}
        {inspection.status !== 'DRAFT' && <span>{t(`inspections.status.${inspection.status}`)}</span>}
        {inspection.round > 0 && <span>{t('inspections.round', { round: inspection.round })}</span>}
        {done && inspection.inspectedAt && (
          <span>{t('inspections.protocol.inspectedOn', { date: formatDate(inspection.inspectedAt, i18n.resolvedLanguage) })}</span>
        )}
        {done && inspection.inspectorName && <span>{t('inspections.protocol.inspectedBy', { name: inspection.inspectorName })}</span>}
      </div>
      {error && <p className="err" role="alert">{t(error)}</p>}

      {states.length === 0 ? (
        <div className="card empty"><h4>{t('inspections.noChecklist')}</h4></div>
      ) : step === 'check' && current ? (
        <>
          <PreviousInspections previous={previous} />
          <div className="tabs" role="tablist" aria-label={t('inspections.areas')}>
            {states.map((state) => (
              <button key={state.area} role="tab" aria-selected={state.area === current.area}
                className={state.area === current.area ? 'on' : undefined} onClick={() => setSelected(state.area)}>
                {areaIcon(state.area)} {state.area}
                {state.deficient > 0 && <span className="badge">{state.deficient}</span>}
              </button>
            ))}
          </div>
          <section className="card">
            <h3>{t('inspections.checklistOf', { area: current.area })}</h3>
            {current.criteria.map((criterion) => (
              <CriterionRow key={criterion.id} criterion={criterion} finding={finding(criterion.id)}
                earlier={earlier.get(criterionKey(criterion.area, criterion.title))} editable={editable} busy={busy}
                onResolve={(item) => void run(() => rateCriterion(inspection.id, item.criterionId, { ...item, rating: 'RESOLVED_ON_SITE' }))}
                onToggleBuilding={(item) => void run(() => rateCriterion(inspection.id, item.criterionId, { ...item, buildingCauseSuspected: !item.buildingCauseSuspected }))}
                onDelete={(item) => void run(() => clearFinding(inspection.id, item.criterionId), t('inspections.defectDeleted'))}
                inspectionId={inspection.id}
                onAddPhoto={(item, file) => void run(() => addPhoto(inspection.id, item.criterionId, file), t('inspections.photo.saved'))}
                onRemovePhoto={(item, photoId) => void run(() => removePhoto(inspection.id, item.criterionId, photoId), t('inspections.photo.removed'))} />
            ))}
            {editable && (
              <div className="acts">
                <button className="btn sec" disabled={busy || current.open === 0}
                  onClick={() => void run(() => markOk(inspection.id, current.area), t('inspections.areaOkDone', { area: current.area }))}>
                  {t('inspections.areaOk')}
                </button>
                <button className="btn" disabled={busy} onClick={() => setDialogOpen(true)}>{t('inspections.addDefect')}</button>
              </div>
            )}
          </section>
        </>
      ) : (
        <>
          <Summary inspection={inspection} states={states} open={open} busy={busy}
            onFinalize={(result) => void run(() => finalizeInspection(inspection.id, result), t('inspections.finalized'))} />
          {done && <Protocol inspectionId={inspection.id} states={states} findings={inspection.findings} />}
        </>
      )}

      {dialogOpen && current && (
        <DefectDialog area={current.area} criteria={current.criteria} findings={inspection.findings} busy={busy}
          onCancel={() => setDialogOpen(false)}
          onSave={async (criterionId: number, severity: Severity, note: string, photo?: File) => {
            const saved = await run(() => rateCriterion(inspection.id, criterionId, { rating: 'DEFICIENT', severity, note }),
              photo ? undefined : t('inspections.defectSaved'))
            if (!saved) return
            setDialogOpen(false)
            // Erst der Mangel, dann das Foto: Scheitert der Upload, bleibt der Mangel erhalten und die Meldung zeigt den Fehler.
            if (photo) await run(() => addPhoto(inspection.id, criterionId, photo), t('inspections.photo.savedWithDefect'))
          }} />
      )}
      {toast && <div className="toast" role="status">{toast}</div>}
    </div>
  )
}

interface CriterionRowProps {
  criterion: Criterion
  finding?: Finding
  earlier?: EarlierDefect
  editable: boolean
  busy: boolean
  onResolve: (finding: Finding) => void
  onToggleBuilding: (finding: Finding) => void
  onDelete: (finding: Finding) => void
  inspectionId: number
  onAddPhoto: (finding: Finding, file: File) => void
  onRemovePhoto: (finding: Finding, photoId: number) => void
}

/**
 * Kriterium der Checkliste. Ein erfasster Mangel steht im aufklappbaren Bereich unter der Beschreibung; Kriterien mit
 * Mangel sind deshalb von Anfang an aufgeklappt und lassen sich über den Pfeil wie gewohnt zuklappen.
 */
function CriterionRow({ criterion, finding, earlier, editable, busy, onResolve, onToggleBuilding, onDelete, inspectionId, onAddPhoto,
  onRemovePhoto }: CriterionRowProps) {
  const { t, i18n } = useTranslation()
  const defect = finding && finding.rating !== 'OK' ? finding : undefined
  return (
    <details className="acc" open={defect ? true : undefined}>
      <summary>
        {criterion.title}
        {earlier && (
          <span className="chip wr ins-earlier">{t('inspections.previous.earlier', { date: formatDate(earlier.inspectedAt, i18n.resolvedLanguage) })}</span>
        )}
        {finding && <span className={`chip ${finding.rating === 'DEFICIENT' ? 'bad' : 'ok'}`}>{t(`inspections.rating.${finding.rating}`)}</span>}
      </summary>
      <div className="in">
        <p className="sm2">{criterion.description}</p>
        {earlier?.defect.note && <p className="sm2 mut">{t('inspections.previous.earlierNote', { note: earlier.defect.note })}</p>}
        {defect && (
          <div className="mg ins-defect" aria-label={t('inspections.defectOf', { criterion: criterion.title })}>
            <div className="ch" style={{ marginBottom: 4 }}>
              <b>{t('inspections.defectLabel')}</b>
              <span className="acts" style={{ marginTop: 0 }}>
                {defect.buildingCauseSuspected && <span className="chip inf">{t('inspections.buildingCause')}</span>}
                {defect.rating === 'RESOLVED_ON_SITE'
                  ? <span className="chip ok">{t('inspections.rating.RESOLVED_ON_SITE')}</span>
                  : defect.severity && <span className="chip bad">{t(`inspections.severity.${defect.severity}`)}</span>}
              </span>
            </div>
            {defect.note && <p className="sm2">{defect.note}</p>}
            <FindingPhotos inspectionId={inspectionId} photoIds={defect.photoIds ?? []}
              label={t('inspections.photo.label', { criterion: criterion.title })} editable={editable} busy={busy}
              canAdd={(defect.photoIds ?? []).length < MAX_PHOTOS}
              onAdd={(file) => onAddPhoto(defect, file)} onRemove={(photoId) => onRemovePhoto(defect, photoId)} />
            {editable && (
              <div className="acts">
                <button className="btn gh sm" disabled={busy || defect.rating === 'RESOLVED_ON_SITE'} onClick={() => onResolve(defect)}>{t('inspections.resolveOnSite')}</button>
                <button className="btn gh sm" disabled={busy} aria-pressed={defect.buildingCauseSuspected} onClick={() => onToggleBuilding(defect)}>{t('inspections.buildingCause')}</button>
                <button className="btn dg sm ins-defect-delete" disabled={busy} onClick={() => onDelete(defect)}>{t('inspections.delete')}</button>
              </div>
            )}
          </div>
        )}
      </div>
    </details>
  )
}

interface SummaryProps {
  inspection: Inspection
  states: AreaState[]
  open: number
  busy: boolean
  onFinalize: (result: OverallResult) => void
}

function Summary({ inspection, states, open, busy, onFinalize }: SummaryProps) {
  const { t } = useTranslation()
  const [result, setResult] = useState<OverallResult>(inspection.overallResult ?? suggestedResult(inspection.findings))
  return (
    <>
      {/* Abgeschlossene Kontrollen zeigen die Bereiche im Protokoll, die Übersicht wäre doppelt. */}
      {inspection.status === 'DRAFT' && <section className="card">
        <h3>{t('inspections.overview')}</h3>
        <div className="tw" style={{ marginTop: 10 }}>
          <table className="tbl">
            <thead><tr><th>{t('inspections.area')}</th><th>{t('inspections.state')}</th></tr></thead>
            <tbody>
              {states.map((state) => (
                <tr key={state.area}>
                  <td>{areaIcon(state.area)} {state.area}</td>
                  <td>
                    {state.deficient > 0
                      ? <span className="chip bad">{t('inspections.deficiencies', { count: state.deficient })}</span>
                      : state.open > 0
                        ? <span className="chip wr">{t('inspections.openCriteria', { count: state.open })}</span>
                        : <span className="chip ok">{t('inspections.inOrder')}</span>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>}
      <section className="card">
        <h3>{t('inspections.overallResult')}</h3>
        {inspection.status === 'DRAFT' ? (
          <>
            <p className="xs mut" style={{ marginTop: 6 }}>{t('inspections.resultHint')}</p>
            <div className="fld">
              <label htmlFor="overall-result">{t('inspections.result')}</label>
              <Select id="overall-result" value={result} onChange={(value) => setResult(value as OverallResult)} options={[
                { value: 'PASSED', label: t('inspections.overall.PASSED') },
                { value: 'FAILED', label: t('inspections.overall.FAILED') },
              ]} />
            </div>
            {open > 0 && <p className="note wr">{t('inspections.rateAllFirst', { count: open })}</p>}
            <button className="btn" disabled={busy || open > 0} onClick={() => onFinalize(result)}>{t('inspections.finalize')}</button>
          </>
        ) : inspection.overallResult ? (
          <p className={`note ${inspection.overallResult === 'PASSED' ? 'ok' : 'bad'}`} style={{ marginTop: 10 }}>
            {t(`inspections.overall.${inspection.overallResult}`)}
          </p>
        ) : null}
      </section>
    </>
  )
}
