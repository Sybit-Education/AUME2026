import { useId, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useFetcher, useFetchers, useLoaderData } from 'react-router'
import BottomSheet from '@/components/BottomSheet'
import LoadingPage from '@/components/LoadingPage'
import TileIcon, { type TileIconName } from '@/components/TileIcon'
import type { SelfCheckActionResult } from './actions'
import type { Checklist, Criterion } from './api'
import type { SelfCheckState } from './selfCheck'

export type ChecklistData = { checklist: Checklist | null; selfCheck?: SelfCheckState | null }

/**
 * Checkliste je Bereich (US-21) mit aufklappbarer Erklärung je Kriterium (US-22) und Selbstcheck der
 * eigenen WG (US-84). Die Seite steht unter dem Reiter „Checkliste“ der Kontroll-Ansicht; die Sprache
 * wählen Studierende unter Mehr → Einstellungen.
 */
export default function ChecklistPage() {
  const { t } = useTranslation()
  const data = useLoaderData<ChecklistData | null>()
  const checked = useCheckedCriteria(data)
  if (!data) return <LoadingPage />

  const selfCheck = data.selfCheck ?? null
  // Abhaken nur mit geladenem Selbstcheck der eigenen WG.
  const canCheck = selfCheck?.status === 'ready' && selfCheck.value !== null

  return (
    <>
      {data.checklist === null ? (
        <section className="card empty">
          <h4>{t('checklist.empty')}</h4>
          <p className="mut">{t('checklist.emptyHint')}</p>
        </section>
      ) : (
        <>
          {selfCheck && <SelfCheckCard state={selfCheck} checklist={data.checklist} checked={checked} />}
          {data.checklist.areas.map((area, index) => (
            <section className="card checklist-area" key={area.name} aria-labelledby={`checklist-area-${index}`}>
              <h3 id={`checklist-area-${index}`}>
                <span className="icon-tile sm" aria-hidden="true"><TileIcon name={areaIcon(area.name)} size={20} /></span>
                {area.name}
              </h3>
              {area.criteria.map((criterion) => (
                <CriterionRow key={criterion.id} criterion={criterion}
                  checked={canCheck ? checked.has(criterion.id) : undefined} />
              ))}
            </section>
          ))}
          <p className="mut xs checklist-hint">{t('checklist.sameCriteria')}</p>
        </>
      )}
    </>
  )
}

/**
 * Abgehakte Kriterien inklusive laufender Änderungen, damit Haken, Zähler und Balken sofort reagieren
 * und nicht erst nach dem Neuladen.
 */
function useCheckedCriteria(data: ChecklistData | null) {
  const fetchers = useFetchers()
  const state = data?.selfCheck
  const checked = new Set(state?.status === 'ready' ? state.value?.checkedCriterionIds ?? [] : [])
  for (const fetcher of fetchers) {
    const form = fetcher.formData
    if (!form) continue
    if (form.get('intent') === 'reset') checked.clear()
    if (form.get('intent') === 'check') {
      const id = Number(form.get('criterion'))
      if (form.get('done') === 'true') checked.add(id)
      else checked.delete(id)
    }
  }
  return checked
}

function SelfCheckCard({ state, checklist, checked }: {
  state: SelfCheckState
  checklist: Checklist
  checked: Set<number>
}) {
  const { t } = useTranslation()
  const headingId = useId()
  const reset = useFetcher<SelfCheckActionResult | null>({ key: 'self-check-reset' })
  const [confirming, setConfirming] = useState(false)
  const ids = checklist.areas.flatMap((area) => area.criteria.map((criterion) => criterion.id))
  const total = ids.length
  const done = ids.filter((id) => checked.has(id)).length
  const percent = total ? Math.round((100 * done) / total) : 0

  return (
    <section className="card self-check" aria-labelledby={headingId}>
      <h3 id={headingId}>{t('checklist.selfCheck')}</h3>
      {state.status === 'failed' ? <p className="err" role="alert">{t('errors.selfCheck')}</p>
        : state.value === null ? (
          <>
            <p className="mut sm2">{t('checklist.selfCheckNoFlat')}</p>
            <div className="acts"><Link className="btn sec" to="/student/flat">{t('checklist.selfCheckJoin')}</Link></div>
          </>
        ) : (
          <>
            <p className="mut sm2">{t('checklist.selfCheckIntro')}</p>
            <div className="pb self-check-bar" role="progressbar" aria-valuenow={percent} aria-valuemin={0}
              aria-valuemax={100} aria-label={t('checklist.selfCheckProgressLabel')}>
              <i style={{ width: `${percent}%` }} />
            </div>
            <p className="self-check-count" aria-live="polite">
              {t('checklist.selfCheckProgress', { done, total })}
            </p>
            {reset.data?.error && <p className="err" role="alert">{t(reset.data.error)}</p>}
            <div className="acts self-check-actions">
              {/* Bleibt stehen, damit nichts springt; ohne Haken gibt es nichts zurückzusetzen. */}
              <button className="btn sm gh" type="button" disabled={done === 0 || reset.state !== 'idle'}
                onClick={() => setConfirming(true)}>
                {t('checklist.reset')}
              </button>
              <span className="mut xs">{t('checklist.resetHint')}</span>
            </div>
            {confirming && (
              // Rückfrage wie beim Löschen im Putzplan: Sheet von unten. Schließt beim Absenden,
              // Haken und Zähler leeren sich sofort.
              <BottomSheet title={t('checklist.resetTitle')} onClose={() => setConfirming(false)}>
                <p className="mut">{t('checklist.resetConfirm')}</p>
                <reset.Form method="post" onSubmit={() => setConfirming(false)}>
                  <input type="hidden" name="intent" value="reset" />
                  <div className="sheet-actions">
                    <button className="btn gh" type="button" onClick={() => setConfirming(false)}>{t('checklist.resetNo')}</button>
                    <button className="btn dg" type="submit">{t('checklist.resetYes')}</button>
                  </div>
                </reset.Form>
              </BottomSheet>
            )}
          </>
        )}
    </section>
  )
}

/** Passendes Icon zum Bereichsnamen (Deutsch oder Englisch); sonst ein neutrales Listen-Icon. */
const areaIcons: [RegExp, TileIconName][] = [
  [/boden|floor/i, 'floor'],
  [/küche|kitchen/i, 'kitchen'],
  [/bad|bath|dusche|shower|wc|toilet/i, 'bath'],
  [/treppe|stair/i, 'stairs'],
]

function areaIcon(name: string): TileIconName {
  return areaIcons.find(([pattern]) => pattern.test(name))?.[1] ?? 'list'
}

/** `checked` undefined: ohne Selbstcheck, nur Lesen. */
function CriterionRow({ criterion, checked }: { criterion: Criterion; checked?: boolean }) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const fetcher = useFetcher<SelfCheckActionResult | null>({ key: `self-check-${criterion.id}` })
  const detailsId = `criterion-${criterion.id}`
  const action = open ? t('checklist.less') : t('checklist.details')
  const classes = ['checklist-criterion', open && 'open', checked !== undefined && 'checkable', checked && 'done']
    .filter(Boolean).join(' ')

  return (
    <div className={classes}>
      <div className="checklist-head">
        {checked !== undefined && (
          <label className="self-check-box">
            <input type="checkbox" checked={checked} aria-label={t('checklist.selfCheckDone', { title: criterion.title })}
              onChange={() => {
                void fetcher.submit({ intent: 'check', criterion: String(criterion.id), done: String(!checked) },
                  { method: 'post' })
              }} />
          </label>
        )}
        <button
          type="button"
          className="checklist-toggle"
          aria-expanded={open}
          aria-controls={detailsId}
          aria-label={`${action}: ${criterion.title}`}
          onClick={() => setOpen((value) => !value)}
        >
          <b>{criterion.title}</b>
          <svg className="checklist-chevron" viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor"
            strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false">
            <path d="m6 9 6 6 6-6" />
          </svg>
        </button>
      </div>
      {fetcher.data?.error && <p className="err xs" role="alert">{t(fetcher.data.error)}</p>}
      <p id={detailsId} className="checklist-explanation" hidden={!open}>
        <b>{t('checklist.explanation')}</b> {criterion.description}
      </p>
    </div>
  )
}
