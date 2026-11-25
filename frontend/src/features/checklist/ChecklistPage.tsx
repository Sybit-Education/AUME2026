import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useLoaderData } from 'react-router'
import LoadingPage from '@/components/LoadingPage'
import { useAuth } from '@/lib/auth/AuthContext'
import type { Checklist, Criterion } from './api'

export type ChecklistData = { checklist: Checklist | null }

/**
 * Checkliste je Bereich (US-21) mit aufklappbarer Erklärung je Kriterium (US-22). Die Seite steht
 * unter dem Reiter „Checkliste“ der Kontroll-Ansicht; die Sprache wählen Studierende unter Mehr → Einstellungen.
 */
export default function ChecklistPage() {
  const { t } = useTranslation()
  const { user } = useAuth()
  const data = useLoaderData<ChecklistData | null>()
  if (!data) return <LoadingPage />
  const processLink = user?.privileges.includes('INFO_PAGE_READ') && (
    <Link className="card checklist-process-link" to="/student/inspection/process">
      {t('checklist.processLink')}
      <span aria-hidden="true">›</span>
    </Link>
  )

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
            <section className="card checklist-area" key={area.name} aria-labelledby={`checklist-area-${index}`}>
              <h3 id={`checklist-area-${index}`}>{area.name}</h3>
              {area.criteria.map((criterion) => <CriterionRow key={criterion.id} criterion={criterion} />)}
            </section>
          ))}
          <p className="mut xs checklist-hint">{t('checklist.sameCriteria')}</p>
        </>
      )}
      {processLink}
    </>
  )
}

function CriterionRow({ criterion }: { criterion: Criterion }) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const detailsId = `criterion-${criterion.id}`
  const action = open ? t('checklist.less') : t('checklist.details')

  return (
    <div className={open ? 'checklist-criterion open' : 'checklist-criterion'}>
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
      <p id={detailsId} className="checklist-explanation" hidden={!open}>
        <b>{t('checklist.explanation')}</b> {criterion.description}
      </p>
    </div>
  )
}
