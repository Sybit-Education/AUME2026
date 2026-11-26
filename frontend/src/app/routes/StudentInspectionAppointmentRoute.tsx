import { Suspense, useEffect } from 'react'
import { Await, Navigate, redirect, useLoaderData, useLocation, type RouteObject } from 'react-router'
import { getInspectionInfo } from '@/features/inspection-process/api'
import InspectionProcessPage from '@/features/inspection-process/InspectionProcessPage'
import { loadAppointmentDetails } from '@/features/student-inspections/api'
import InspectionDetailsPage from '@/features/student-inspections/InspectionDetailsPage'
import { protectedLoader } from '@/lib/auth/loaders'
import { SessionExpired } from '@/lib/auth/response'
import { forgetUser } from '@/lib/auth/session'
import { UiError } from '@/lib/errors'

async function loadSection<T>(request: Promise<T>, signal: AbortSignal) {
  try {
    const value = await request
    return { kind: 'ready' as const, value }
  } catch (cause) {
    if (signal.aborted || cause instanceof SessionExpired) return { kind: 'cancelled' as const }
    if (cause instanceof UiError && cause.key === 'errors.sessionExpired') {
      forgetUser()
      return { kind: 'cancelled' as const }
    }
    if (cause instanceof Response && cause.headers.get('Location') === '/forbidden') {
      return { kind: 'forbidden' as const }
    }
    return { kind: 'failed' as const }
  }
}

const loader = protectedLoader(undefined, async ({ request }, user) => {
  if (!user.roles.includes('STUDENT')) throw redirect('/forbidden')
  const { signal } = request
  return {
    appointment: loadSection(loadAppointmentDetails(signal), signal),
    info: user.privileges.includes('INFO_PAGE_READ')
      ? loadSection(getInspectionInfo(signal), signal)
      : null,
  }
})

// eslint-disable-next-line react-refresh/only-export-components -- The router imports the composed route with its loader.
function StudentInspectionAppointmentPage() {
  const data = useLoaderData<typeof loader>()
  const { hash } = useLocation()
  const info = data?.info
  useEffect(() => {
    if (info && hash === '#inspection-process') {
      document.getElementById('inspection-process')?.scrollIntoView?.()
    }
  }, [hash, info])
  if (!data) return <InspectionDetailsPage loading />

  return (
    <>
      <Suspense fallback={<InspectionDetailsPage loading />}>
        <Await resolve={data.appointment}>
          {(result: Awaited<typeof data.appointment>) => {
            if (result.kind === 'forbidden') return <Navigate to="/forbidden" replace />
            if (result.kind === 'cancelled') return null
            return result.kind === 'ready'
              ? <InspectionDetailsPage appointment={result.value} />
              : <InspectionDetailsPage loadFailed />
          }}
        </Await>
      </Suspense>
      {info && (
        <Suspense fallback={<InspectionProcessPage />}>
          <Await resolve={info}>
            {(result: Awaited<typeof info>) => {
              if (result.kind === 'cancelled') return null
              return result.kind === 'ready'
                ? <InspectionProcessPage info={result.value} />
                : <InspectionProcessPage loadFailed />
            }}
          </Await>
        </Suspense>
      )}
    </>
  )
}

export const studentInspectionAppointmentRoute: RouteObject = {
  path: 'appointment',
  loader,
  Component: StudentInspectionAppointmentPage,
}
