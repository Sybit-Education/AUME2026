import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useLoaderData } from 'react-router'
import LoadingPage from '@/components/LoadingPage'
import type { Checklist, Criterion } from './api'

export type ChecklistData = { checklist: Checklist | null }

/**
 * Checkliste je Bereich (US-21) mit aufklappbarer Erklärung je Kriterium (US-22). Die Seite steht
 * unter dem Reiter „Checkliste“ der Kontroll-Ansicht; die Sprache wählen Studierende unter Mehr → Einstellungen.
 */
export default function ChecklistPage() {
  const { t } = useTranslation()
  const data = useLoaderData<ChecklistData | null>()
  if (!data) return <LoadingPage />

  return (
    <>
      {data.checklist === null ? (
        <section className="card empty">
          <h4>{t('checklist.empty')}</h4>
          <p className="mut">{t('checklist.emptyHint')}</p>
        </section>
      ) : (
        <>
          {data.checklist.areas.map((area, index) => (
            <section className="card" key={area.name} aria-labelledby={`checklist-area-${index}`}>
              <h3 id={`checklist-area-${index}`}>{area.name}</h3>
              {area.criteria.map((criterion) => <CriterionRow key={criterion.id} criterion={criterion} />)}
            </section>
          ))}
          <p className="mut xs checklist-hint">{t('checklist.sameCriteria')}</p>
        </>
      )}
    </>
  )
}

function CriterionRow({ criterion }: { criterion: Criterion }) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const detailsId = `criterion-${criterion.id}`
  const action = open ? t('checklist.less') : t('checklist.details')

  return (
    <div className="checklist-criterion">
      <div className="row">
        <b>{criterion.title}</b>
        <button
          type="button"
          className="btn sm gh"
          aria-expanded={open}
          aria-controls={detailsId}
          aria-label={`${action}: ${criterion.title}`}
          onClick={() => setOpen((value) => !value)}
        >
          {action}
        </button>
      </div>
      <p id={detailsId} className="checklist-explanation" hidden={!open}>
        <b>{t('checklist.explanation')}</b> {criterion.description}
      </p>
    </div>
  )
}
