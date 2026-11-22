import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useLoaderData, useLocation, useNavigate } from 'react-router'
import LoadingPage from '@/components/LoadingPage'
import { SessionExpired } from '@/lib/auth/response'
import { loginDestination } from '@/lib/auth/returnTo'
import { forgetUser } from '@/lib/auth/session'
import { errorKey, UiError, type ErrorKey } from '@/lib/errors'
import {
  clearFinding, finalizeInspection, getInspection, markOk, rateCriterion, startInspection,
  type Criterion, type Finding, type Inspection, type OverallResult, type PreviousInspection, type Severity,
} from './api'
import { areaIcon, areas, suggestedResult, type AreaState } from './summary'
import DefectDialog from './DefectDialog'
import { formatDate } from './history'
import Protocol from './Protocol'
import { criterionKey, earlierDefects, type EarlierDefect } from './previous'
import PreviousInspections from './PreviousInspections'
import './inspections.css'

export interface InspectionLoaderData {
  inspection: Inspection
  /** Vorgeschichte der WG (US-51), neueste zuerst. */
  previous: PreviousInspection[]
}

export default function InspectionPage() {
  const loaded = useLoaderData<InspectionLoaderData | null>()
  if (!loaded) return <LoadingPage />
  return <InspectionView key={loaded.inspection.id} initial={loaded.inspection} previous={loaded.previous} />
}

function InspectionView({ initial, previous }: { initial: Inspection; previous: PreviousInspection[] }) {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const location = useLocation()
  const [inspection, setInspection] = useState(initial)
  const [step, setStep] = useState<'check' | 'summary'>(initial.status === 'DRAFT' || initial.status === 'PLANNED' ? 'check' : 'summary')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<ErrorKey | null>(null)
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
        </div>
      </div>
      <div className="ins-meta">
        <span>{inspection.residenceName}</span>
        <span>{t('inspections.places', { count: inspection.capacity })}</span>
        <span>{t(`inspections.status.${inspection.status}`)}</span>
        {inspection.round > 0 && <span>{t('inspections.round', { round: inspection.round })}</span>}
        {done && inspection.inspectedAt && (
          <span>{t('inspections.protocol.inspectedOn', { date: formatDate(inspection.inspectedAt, i18n.resolvedLanguage) })}</span>
        )}
        {done && inspection.inspectorName && <span>{t('inspections.protocol.inspectedBy', { name: inspection.inspectorName })}</span>}
      </div>
      {done && <p className="xs"><Link to="/inspections/history">{t('inspections.protocol.backToHistory')}</Link></p>}
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
                earlier={earlier.get(criterionKey(criterion.area, criterion.title))} />
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
          <DefectList area={current} findings={inspection.findings} editable={editable} busy={busy}
            onResolve={(item) => void run(() => rateCriterion(inspection.id, item.criterionId, { ...item, rating: 'RESOLVED_ON_SITE' }))}
            onToggleBuilding={(item) => void run(() => rateCriterion(inspection.id, item.criterionId, { ...item, buildingCauseSuspected: !item.buildingCauseSuspected }))}
            onDelete={(item) => void run(() => clearFinding(inspection.id, item.criterionId), t('inspections.defectDeleted'))} />
        </>
      ) : (
        <>
          <Summary inspection={inspection} states={states} open={open} busy={busy}
            onFinalize={(result) => void run(() => finalizeInspection(inspection.id, result), t('inspections.finalized'))} />
          {done && <Protocol states={states} findings={inspection.findings} />}
        </>
      )}

      {dialogOpen && current && (
        <DefectDialog area={current.area} criteria={current.criteria} findings={inspection.findings} busy={busy}
          onCancel={() => setDialogOpen(false)}
          onSave={async (criterionId: number, severity: Severity, note: string) => {
            if (await run(() => rateCriterion(inspection.id, criterionId, { rating: 'DEFICIENT', severity, note }), t('inspections.defectSaved'))) {
              setDialogOpen(false)
            }
          }} />
      )}
      {toast && <div className="toast" role="status">{toast}</div>}
    </div>
  )
}

function CriterionRow({ criterion, finding, earlier }: { criterion: Criterion; finding?: Finding; earlier?: EarlierDefect }) {
  const { t, i18n } = useTranslation()
  return (
    <details className="acc">
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
      </div>
    </details>
  )
}

interface DefectListProps {
  area: AreaState
  findings: Finding[]
  editable: boolean
  busy: boolean
  onResolve: (finding: Finding) => void
  onToggleBuilding: (finding: Finding) => void
  onDelete: (finding: Finding) => void
}

function DefectList({ area, findings, editable, busy, onResolve, onToggleBuilding, onDelete }: DefectListProps) {
  const { t } = useTranslation()
  const defects = area.criteria.flatMap((criterion) => {
    const item = findings.find((entry) => entry.criterionId === criterion.id)
    return item && item.rating !== 'OK' ? [{ criterion, item }] : []
  })
  if (defects.length === 0) return null
  return (
    <section className="card">
      <h3>{t('inspections.defects')}</h3>
      {defects.map(({ criterion, item }) => (
        <div className="mg" key={criterion.id}>
          <div className="ch" style={{ marginBottom: 4 }}>
            <b>{criterion.title}</b>
            <span className="acts" style={{ marginTop: 0 }}>
              {item.buildingCauseSuspected && <span className="chip inf">{t('inspections.buildingCause')}</span>}
              {item.rating === 'RESOLVED_ON_SITE'
                ? <span className="chip ok">{t('inspections.rating.RESOLVED_ON_SITE')}</span>
                : item.severity && <span className="chip bad">{t(`inspections.severity.${item.severity}`)}</span>}
            </span>
          </div>
          {item.note && <p className="sm2">{item.note}</p>}
          {editable && (
            <div className="acts">
              <button className="btn gh sm" disabled={busy || item.rating === 'RESOLVED_ON_SITE'} onClick={() => onResolve(item)}>{t('inspections.resolveOnSite')}</button>
              <button className="btn gh sm" disabled={busy} aria-pressed={item.buildingCauseSuspected} onClick={() => onToggleBuilding(item)}>{t('inspections.buildingCause')}</button>
              <button className="btn gh sm" disabled={busy} onClick={() => onDelete(item)}>{t('inspections.delete')}</button>
            </div>
          )}
        </div>
      ))}
    </section>
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
      <section className="card">
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
      </section>
      <section className="card">
        <h3>{t('inspections.overallResult')}</h3>
        {inspection.status === 'DRAFT' ? (
          <>
            <p className="xs mut" style={{ marginTop: 6 }}>{t('inspections.resultHint')}</p>
            <div className="fld">
              <label htmlFor="overall-result">{t('inspections.result')}</label>
              <select id="overall-result" value={result} onChange={(event) => setResult(event.target.value as OverallResult)}>
                <option value="PASSED">{t('inspections.overall.PASSED')}</option>
                <option value="FAILED">{t('inspections.overall.FAILED')}</option>
              </select>
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
