import { useId } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import LoadingPage from '@/components/LoadingPage'
import { createInspectionCalendar } from './calendar'
import { formatInspectionPeriod } from './formatInspectionPeriod'
import type { AppointmentDetails, UpcomingAppointment } from './api'
import './inspection-appointment.css'

export default function InspectionAppointment({
  appointment,
  variant = 'details',
  loading = false,
  loadFailed = false,
  canReadChecklist = false,
}: {
  appointment: UpcomingAppointment | AppointmentDetails | null
  variant?: 'summary' | 'details'
  loading?: boolean
  loadFailed?: boolean
  canReadChecklist?: boolean
}) {
  const { t, i18n } = useTranslation()
  const headingId = useId()
  const summary = variant === 'summary'
  const detail = appointment && 'display' in appointment ? appointment : undefined
  const resultAvailable = detail?.status === 'RESULT_AVAILABLE'
  const heading = summary || detail?.display === 'UPCOMING' ? t('studentInspections.title')
    : resultAvailable ? t('studentInspections.lastTitle')
    : detail ? t('studentInspections.periodTitle') : t('studentInspections.detailsTitle')
  const windowStart = detail?.windowStart
  const windowEnd = detail?.windowEnd
  const validTime = /^(?:[01]\d|2[0-3]):[0-5]\d$/
  const showWindow = detail?.display !== 'PAST' && windowStart && windowEnd
    && validTime.test(windowStart) && validTime.test(windowEnd) && windowStart < windowEnd

  function downloadCalendar() {
    if (!appointment) return
    const calendar = createInspectionCalendar(appointment.id, appointment.startsOn, appointment.endsOn, new Date())
    const link = document.createElement('a')
    link.href = `data:text/calendar;charset=utf-8,${encodeURIComponent(calendar)}`
    link.download = 'seezeit-kontrolltermin.ics'
    link.click()
  }

  return (
    <section className="card" aria-labelledby={headingId}>
      <div className="inspection-appointment-heading">
        <h2 id={headingId}>{heading}</h2>
        {!loading && !loadFailed && appointment && (
          appointment.appointmentChanged
            ? <span className="chip wr">{t('studentInspections.changed')}</span>
            : appointment.showInitialInspection && <span className="chip inf">{t('studentResult.initial')}</span>
        )}
      </div>
      {loading ? <LoadingPage /> : appointment ? (
        <>
          {!summary && <p className="lbl">{t('studentInspections.period')}</p>}
          <p className="big">{formatInspectionPeriod(appointment.startsOn, appointment.endsOn, i18n.resolvedLanguage)}</p>
          {showWindow && <p className="mut sm2">{t('studentInspections.window', { start: windowStart, end: windowEnd })}</p>}
          {!summary && !loadFailed && detail?.status && (
            <ol className="inspection-status-timeline" role="list" data-complete={resultAvailable}>
              <li className="inspection-status-step" data-reached="true" aria-current={resultAvailable ? undefined : 'step'}>
                {t('studentInspections.announced')}
              </li>
              <li className="inspection-status-step" data-reached={resultAvailable} aria-current={resultAvailable ? 'step' : undefined}>
                {t('studentResult.available')}
              </li>
            </ol>
          )}
          <div className="acts">
            {summary ? <Link className="btn" to="/student/inspection/appointment">{t('studentInspections.details')}</Link> : detail?.display !== 'PAST' && (
              <button type="button" className="btn" onClick={downloadCalendar}>
                {t('studentInspections.download')}
              </button>
            )}
            {canReadChecklist && (
              <Link className="btn sec" to="/student/inspection/checklist">{t('studentControl.checklist')}</Link>
            )}
          </div>
        </>
      ) : loadFailed ? (
        <p className="err" role="alert">{t('errors.studentInspectionAppointment')}</p>
      ) : <p>{t('studentInspections.empty')}</p>}
    </section>
  )
}
