import { useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import LoadingPage from '@/components/LoadingPage'
import type { InspectionInfo } from './api'
import './inspection-process.css'

type SectionKey = 'process' | 'consequences' | 'standards'

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
export default function InspectionProcessPage({
  info, loadFailed,
}: { info?: InspectionInfo; loadFailed?: boolean }) {
  const { t, i18n } = useTranslation()
  const english = i18n.resolvedLanguage === 'en'

  return (
    <section className="card inspection-process-card" aria-labelledby="inspection-process">
      <h2 id="inspection-process" className="inspection-process-title">{t('inspectionProcess.title')}</h2>
      {loadFailed ? <p className="err" role="alert">{t('errors.infoPages')}</p> : !info ? <LoadingPage /> : (
        <div className="process-sections">
          {sections.map(({ key, important }) => {
            const translated = english ? info[`${key}En`] : ''
            const hasTranslation = translated.trim() !== ''
            return (
              <InfoSection
                key={key}
                id={key}
                title={t(`inspectionProcess.${key}`)}
                text={hasTranslation ? translated : info[key]}
                germanFallback={english && !hasTranslation && info[key].trim() !== ''}
                important={important}
              />
            )
          })}
        </div>
      )}
    </section>
  )
}

function InfoSection({
  id, title, text, germanFallback, important = false,
}: { id: SectionKey; title: string; text: string; germanFallback: boolean; important?: boolean }) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(important)
  const bodyId = `inspection-process-${id}`
  const classes = ['process-section', important && 'important', open && 'open'].filter(Boolean).join(' ')

  return (
    <section className={classes} aria-labelledby={`${bodyId}-title`}>
      <h3 id={`${bodyId}-title`}>
        <button type="button" className="process-toggle" aria-expanded={open} aria-controls={bodyId} onClick={() => setOpen((value) => !value)}>
          <span className="process-toggle-label">
            <span>{title}</span>
            {important && <span className="process-badge">{t('inspectionProcess.important')}</span>}
          </span>
          <svg className="process-chevron" viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor"
            strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false">
            <path d="m6 9 6 6 6-6" />
          </svg>
        </button>
      </h3>
      <div id={bodyId} className="process-body" hidden={!open}>
        {germanFallback && <p className="mut xs">{t('inspectionProcess.germanFallback')}</p>}
        {text.trim() === ''
          ? <p className="mut">{t('inspectionProcess.empty')}</p>
          : <InfoText text={text} language={germanFallback ? 'de' : undefined} />}
      </div>
    </section>
  )
}

/** Zeigt den Text mit seinen Zeilenumbrüchen; Sätze zu Kosten sind fett und farbig markiert. */
function InfoText({ text, language }: { text: string; language?: string }) {
  return text.split(/\n+/).filter((line) => line.trim() !== '').map((line, index) => (
    <p key={index} lang={language}>{costPattern.test(line) ? highlightCosts(line) : line}</p>
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
