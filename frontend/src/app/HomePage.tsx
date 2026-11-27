import { useLoaderData } from 'react-router'
import { useAuth } from '@/lib/auth/AuthContext'
import DashboardPage from '@/features/dashboard/DashboardPage'
import ConsequenceNotice from '@/features/inspection-process/ConsequenceNotice'
import WeekTasksCard from '@/features/putzplan/WeekTasksCard'
import StudentInspectionDashboardCard from '@/features/student-inspections/DashboardInspection'
import MembershipChips from '@/features/wg/MembershipChips'
import InspectionOverviewPage from '@/features/inspection-overview/InspectionOverviewPage'
import { INSPECTION_OVERVIEW_PRIVILEGE } from '@/features/inspection-overview/routes'
import OverviewPage from '@/features/inspections/OverviewPage'
import { CARETAKER_OVERVIEW_PRIVILEGE } from '@/features/inspections/currentInspections'
import type { HomeData } from './studentHome'

/**
 * Im Tab „Übersicht“ sieht die Sachbearbeitung den Stand der Kontrollen (US-47), die Objektbetreuung
 * ihre Übersicht (#41), alle anderen das Dashboard. Für Studierende bündelt es WG, Kontrolle,
 * Putzaufgaben der Woche und den Konsequenzhinweis (#99).
 */
export default function HomePage() {
  const { user } = useAuth()
  if (user?.privileges.includes(INSPECTION_OVERVIEW_PRIVILEGE)) return <InspectionOverviewPage />
  if (user?.privileges.includes(CARETAKER_OVERVIEW_PRIVILEGE)) return <OverviewPage />
  return <Dashboard />
}

function Dashboard() {
  const { user } = useAuth()
  const data = useLoaderData<HomeData | null>()
  const membership = data?.membership
  const plan = data?.plan
  // Studierende sehen statt der Begrüßungskarte einen schlichten Kopf wie im Wireframe (#99).
  // „Hallo, Name“ steht in der Kopfleiste (Route-Handle), hier nur noch die eigene WG.
  const header = user?.roles.includes('STUDENT') ? (membership ? (
    <MembershipChips
      membership={membership.status === 'ready' ? membership.value : null}
      loadFailed={membership.status === 'failed'}
    />
  ) : <></>) : undefined

  return (
    <DashboardPage header={header}>
      <StudentInspectionDashboardCard />
      {plan && (
        <WeekTasksCard
          plan={plan.status === 'ready' ? plan.value : null}
          loadFailed={plan.status === 'failed'}
          action="/?index"
        />
      )}
      <ConsequenceNotice />
    </DashboardPage>
  )
}
