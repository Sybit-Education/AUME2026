import { useEffect, useId, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useFetcher, useLoaderData } from 'react-router'
import LoadingPage from '@/components/LoadingPage'
import { useAuth } from '@/lib/auth/AuthContext'
import { SessionExpired } from '@/lib/auth/response'
import { forgetUser } from '@/lib/auth/session'
import { UiError } from '@/lib/errors'
import type { MessageReadResult } from './actions'
import { checklistFileName, downloadMessageChecklist, type StudentMessage, type StudentMessages } from './api'
import { formatMessageDate, formatMessagePeriod } from './formatMessageDates'
import './student-messages.css'

export default function StudentMessagesPage() {
  const { t } = useTranslation()
  const { user } = useAuth()
  const messages = useLoaderData<StudentMessages | null>()
  if (!messages) return <LoadingPage />
  const { content, page } = messages

  return (
    <div className="student-messages">
      {content.length === 0 && <p className="card mut">{t('studentMessages.empty')}</p>}
      {content.map((message) => (
        <MessageCard key={message.id} message={message}
          canReadChecklist={user?.privileges.includes('CHECKLIST_READ') ?? false} />
      ))}
      {(page.number > 0 || page.totalPages > 1) && (
        <nav className="acts student-messages-pagination" aria-label={t('studentMessages.pagination')}>
          {page.number > 0 && (
            <Link className="btn sec" to={`?page=${Math.min(page.number - 1, Math.max(0, page.totalPages - 1))}`}>
              {t('studentMessages.previous')}
            </Link>
          )}
          {page.number < page.totalPages - 1 && (
            <Link className="btn sec" to={`?page=${page.number + 1}`}>{t('studentMessages.next')}</Link>
          )}
        </nav>
      )}
    </div>
  )
}

const validTime = /^(?:[01]\d|2[0-3]):[0-5]\d$/

function MessageCard({ message, canReadChecklist }: { message: StudentMessage; canReadChecklist: boolean }) {
  const { t, i18n } = useTranslation()
  const language = i18n.resolvedLanguage
  const [open, setOpen] = useState(false)
  const bodyId = useId()
  const read = useFetcher<MessageReadResult>()
  const unread = message.readAt === null && !(read.data && 'read' in read.data)
  const readError = unread && read.data && 'error' in read.data ? read.data.error : undefined
  const announcement = message.type === 'ANNOUNCEMENT'
  const hasAttachment = announcement && message.hasChecklistPdf
  const title = announcement
    ? t('studentMessages.announcementTitle', { period: formatMessagePeriod(message.startsOn, message.endsOn, language) })
    : t(message.type === 'REMINDER_7_DAYS' ? 'studentMessages.reminderWeekTitle' : 'studentMessages.reminderTomorrowTitle')
  const period = formatMessagePeriod(message.startsOn, message.endsOn, language, true)
  const { windowStart, windowEnd } = message
  const showWindow = windowStart && windowEnd && validTime.test(windowStart)
    && validTime.test(windowEnd) && windowStart < windowEnd

  function markRead() {
    if (unread && read.state === 'idle') {
      void read.submit({ id: String(message.id) }, { method: 'post' })
    }
  }

  return (
    <article className="card student-message">
      <h2 className="student-message-heading">
        <button type="button" className="student-message-toggle" aria-expanded={open} aria-controls={bodyId}
          onClick={() => {
            if (!open) markRead()
            setOpen(!open)
          }}>
          <span className="student-message-heading-text">
            <span className="student-message-meta">
              <span className={announcement ? 'chip t' : 'chip'}>
                {t(announcement ? 'studentMessages.announcement' : 'studentMessages.reminder')}
              </span>
              <time className="mut xs" dateTime={message.occurredAt}>{formatMessageDate(message.occurredAt, language)}</time>
              {unread && <span className="chip inf">{t('studentMessages.new')}</span>}
            </span>
            <span>{title}</span>
          </span>
          <svg className="student-message-chevron" viewBox="0 0 24 24" width="20" height="20"
            fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <path d="m6 9 6 6 6-6" />
          </svg>
        </button>
      </h2>
      <div id={bodyId} className="student-message-body" hidden={!open}>
        <p>{announcement
          ? t('studentMessages.announcementText', { flat: message.flatLabel, period })
          : t('studentMessages.reminderText', { period })}</p>
        {announcement && showWindow && (
          <p>{t('studentInspections.window', { start: windowStart, end: windowEnd })}</p>
        )}
        <p>{t(announcement ? 'studentMessages.prepareAnnouncement' : 'studentMessages.prepareReminder')}
          {hasAttachment && <> {t('studentMessages.attachmentHint')}</>}
        </p>
        {hasAttachment && <ChecklistDownload id={message.id} language={language} />}
        {!announcement && canReadChecklist && (
          <div className="acts">
            <Link className="btn sec" to="/student/inspection/checklist">{t('studentMessages.openChecklist')}</Link>
          </div>
        )}
        {readError && (
          <>
            <p className="err" role="alert">{t(readError)}</p>
            <button className="btn sec" type="button" onClick={markRead} disabled={read.state !== 'idle'}>
              {t('studentMessages.retryRead')}
            </button>
          </>
        )}
      </div>
    </article>
  )
}

function ChecklistDownload({ id, language }: { id: number; language?: string }) {
  const { t } = useTranslation()
  const [pending, setPending] = useState(false)
  const [failed, setFailed] = useState(false)
  const request = useRef<AbortController | null>(null)
  useEffect(() => () => request.current?.abort(), [])

  async function download() {
    if (request.current) return
    const controller = new AbortController()
    request.current = controller
    setPending(true)
    setFailed(false)
    try {
      await downloadMessageChecklist(id, language, controller.signal)
    } catch (cause) {
      if (controller.signal.aborted || cause instanceof SessionExpired) return
      if (cause instanceof UiError && cause.key === 'errors.sessionExpired') forgetUser()
      else setFailed(true)
    } finally {
      request.current = null
      if (!controller.signal.aborted) setPending(false)
    }
  }

  const file = checklistFileName(language)
  return (
    <>
      <div className="acts">
        <button className="btn sec student-message-file" type="button" disabled={pending}
          aria-label={t('studentMessages.download', { file })} onClick={() => void download()}>
          {file}
        </button>
      </div>
      {failed && <p className="err" role="alert">{t('errors.studentMessageDownload')}</p>}
    </>
  )
}
