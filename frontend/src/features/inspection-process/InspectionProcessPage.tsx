import { useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useLoaderData } from 'react-router'
import LoadingPage from '@/components/LoadingPage'
import type { InspectionInfo } from './api'

export type InspectionProcessData = { info: InspectionInfo }

type SectionKey = keyof InspectionInfo

/** Reihenfolge der Bereiche; „Konsequenzen“ ist hervorgehoben und startet aufgeklappt. */
const sections: { key: SectionKey; important?: boolean }[] = [
  { key: 'process' },
  { key: 'consequences', important: true },
  { key: 'standards' },
]

/** Sätze zu Kosten werden zusätzlich markiert. */
const costPattern = /kosten|€|euro|gebühr|cost|fee|charge/i

/**
 * Ablauf und Konsequenzen einer Kontrolle (US-23). Bewusst ohne Zeitstrahl: aufklappbare Bereiche
 * im Stil der Checkliste. Die Texte pflegt die Sachbearbeitung unter „Informationen“.
 */
export default function InspectionProcessPage() {
  const { t, i18n } = useTranslation()
  const data = useLoaderData<InspectionProcessData | null>()
  if (!data) return <LoadingPage />

  return (
    <>
      <Link className="back" to="/student/inspection/checklist">‹ {t('inspectionProcess.back')}</Link>
      <div className="ch"><h2>{t('inspectionProcess.title')}</h2></div>
      {i18n.resolvedLanguage !== 'de' && <p className="mut xs">{t('inspectionProcess.germanOnly')}</p>}
      {sections.map(({ key, important }) => (
        <InfoSection key={key} id={key} title={t(`inspectionProcess.${key}`)} text={data.info[key]} important={important} />
      ))}
    </>
  )
}

function InfoSection({ id, title, text, important = false }: { id: SectionKey; title: string; text: string; important?: boolean }) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(important)
  const bodyId = `inspection-process-${id}`
  const classes = ['card', 'process-section', important && 'important', open && 'open'].filter(Boolean).join(' ')

  return (
    <section className={classes} aria-labelledby={`${bodyId}-title`}>
      <h3 id={`${bodyId}-title`}>
        <button type="button" className="process-toggle" aria-expanded={open} aria-controls={bodyId} onClick={() => setOpen((value) => !value)}>
          <span>
            {title}
            {important && <span className="process-badge">{t('inspectionProcess.important')}</span>}
          </span>
          <svg className="process-chevron" viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor"
            strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false">
            <path d="m6 9 6 6 6-6" />
          </svg>
        </button>
      </h3>
      <div id={bodyId} className="process-body" hidden={!open}>
        {text.trim() === '' ? <p className="mut">{t('inspectionProcess.empty')}</p> : <InfoText text={text} />}
      </div>
    </section>
  )
}

/** Zeigt den Text mit seinen Zeilenumbrüchen; Sätze zu Kosten sind fett und farbig markiert. */
function InfoText({ text }: { text: string }) {
  return text.split(/\n+/).filter((line) => line.trim() !== '').map((line, index) => (
    <p key={index}>{costPattern.test(line) ? highlightCosts(line) : line}</p>
  ))
}

/** Satzende nur nach einem Buchstaben, damit Aufzählungen wie „1. …“ nicht getrennt werden. */
const sentenceEnd = /(?<=\p{L}[.!?])\s+/u

function highlightCosts(line: string): ReactNode[] {
  return line.split(sentenceEnd).map((sentence, part) => (
    <span key={part}>
      {part > 0 && ' '}
      {costPattern.test(sentence) ? <strong className="process-cost">{sentence}</strong> : sentence}
    </span>
  ))
}
