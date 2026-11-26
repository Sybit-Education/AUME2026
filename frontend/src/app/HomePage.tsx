import { useAuth } from '@/lib/auth/AuthContext'
import DashboardPage from '@/features/dashboard/DashboardPage'
import StudentInspectionDashboardCard from '@/features/student-inspections/DashboardInspection'
import InspectionOverviewPage from '@/features/inspection-overview/InspectionOverviewPage'
import { INSPECTION_OVERVIEW_PRIVILEGE } from '@/features/inspection-overview/routes'
import OverviewPage from '@/features/overview/OverviewPage'
import { CARETAKER_OVERVIEW_PRIVILEGE } from '@/features/overview/routes'

/**
 * Im Tab „Übersicht“ sieht die Sachbearbeitung den Stand der Kontrollen (US-47), die Objektbetreuung
 * ihre Übersicht (#41), alle anderen das Dashboard.
 */
export default function HomePage() {
  const { user } = useAuth()
  if (user?.privileges.includes(INSPECTION_OVERVIEW_PRIVILEGE)) return <InspectionOverviewPage />
  if (user?.privileges.includes(CARETAKER_OVERVIEW_PRIVILEGE)) return <OverviewPage />

  return (
    <DashboardPage>
      <StudentInspectionDashboardCard />
    </DashboardPage>
  )
}
