import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import { useAuth } from '@/lib/auth/AuthContext'
import type { LoggedInUser } from '@/lib/auth/client'
import { SessionExpired } from '@/lib/auth/response'
import { forgetUser, getSession, subscribe } from '@/lib/auth/session'
import { UiError } from '@/lib/errors'
import InspectionAppointment from './InspectionAppointment'
import { loadAppointment } from './api'

type AppointmentResult = { user: LoggedInUser } & (
  | { status: 'loaded'; appointment: Awaited<ReturnType<typeof loadAppointment>> }
  | { status: 'error' }
)

export default function DashboardInspection() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const [result, setResult] = useState<AppointmentResult>()

  useEffect(() => {
    const student = user
    if (!student?.roles.includes('STUDENT') || getSession().user !== student) return

    const controller = new AbortController()
    const { signal } = controller
    // Cancel as soon as the session changes, even before React runs effect cleanup.
    const unsubscribe = subscribe(() => {
      if (getSession().user !== student) controller.abort()
    })

    async function load(currentStudent: LoggedInUser) {
      try {
        const appointment = await loadAppointment(currentStudent, signal)
        if (!signal.aborted) setResult({ user: currentStudent, status: 'loaded', appointment })
      } catch (cause) {
        if (signal.aborted || cause instanceof SessionExpired) return
        if (cause instanceof UiError && cause.key === 'errors.sessionExpired') {
          forgetUser()
          return
        }
        // A redirect response thrown outside a route loader needs explicit navigation.
        if (cause instanceof Response && cause.headers.get('Location') === '/forbidden') {
          void navigate('/forbidden', { replace: true })
          return
        }
        setResult({ user: currentStudent, status: 'error' })
      }
    }

    void load(student)
    return () => {
      unsubscribe()
      controller.abort()
    }
  }, [navigate, user])

  if (!user?.roles.includes('STUDENT')) return null

  const currentResult = result?.user === user ? result : undefined
  return (
    <InspectionAppointment
      appointment={currentResult?.status === 'loaded' ? currentResult.appointment : null}
      loading={!currentResult}
      loadFailed={currentResult?.status === 'error'}
      variant="summary"
      canReadChecklist={user.privileges.includes('CHECKLIST_READ')}
    />
  )
}
