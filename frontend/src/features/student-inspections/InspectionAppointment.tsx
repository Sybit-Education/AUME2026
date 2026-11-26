import { useId } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import LoadingPage from '@/components/LoadingPage'
import { createInspectionCalendar } from './calendar'
import { formatInspectionPeriod } from './formatInspectionPeriod'
import type { UpcomingAppointment } from './api'
import './inspection-appointment.css'

export default function InspectionAppointment({
  appointment,
  variant = 'details',
  loading = false,
  loadFailed = false,
}: { appointment: UpcomingAppointment | null; variant?: 'summary' | 'details'; loading?: boolean; loadFailed?: boolean }) {
  const { t, i18n } = useTranslation()
  const headingId = useId()
  const summary = variant === 'summary'

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
        <h2 id={headingId}>{summary ? t('studentInspections.title') : t('studentInspections.detailsTitle')}</h2>
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
          <div className="acts">
            {summary ? <Link className="btn" to="/student/inspection/appointment">{t('studentInspections.details')}</Link> : (
              <button type="button" className="btn" onClick={downloadCalendar}>
                {t('studentInspections.download')}
              </button>
            )}
          </div>
        </>
      ) : loadFailed ? (
        <p className="err" role="alert">{t('errors.studentInspectionAppointment')}</p>
      ) : <p>{t('studentInspections.empty')}</p>}
    </section>
  )
}
