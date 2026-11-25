import { useAuth } from '@/lib/auth/AuthContext'
import DashboardPage from '@/features/dashboard/DashboardPage'
import InspectionOverviewPage from '@/features/inspection-overview/InspectionOverviewPage'
import { INSPECTION_OVERVIEW_PRIVILEGE } from '@/features/inspection-overview/routes'

/** Im Tab „Übersicht“ sieht die Sachbearbeitung den Stand der Kontrollen (US-47), alle anderen das Dashboard. */
export default function HomePage() {
  const { user } = useAuth()
  return user?.privileges.includes(INSPECTION_OVERVIEW_PRIVILEGE) ? <InspectionOverviewPage /> : <DashboardPage />
}
