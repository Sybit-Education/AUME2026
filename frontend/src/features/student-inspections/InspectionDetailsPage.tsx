import { useAuth } from '@/lib/auth/AuthContext'
import InspectionAppointment from './InspectionAppointment'
import type { AppointmentDetails } from './api'

export default function InspectionDetailsPage({
  appointment = null, loading, loadFailed,
}: { appointment?: AppointmentDetails | null; loading?: boolean; loadFailed?: boolean }) {
  const { user } = useAuth()
  return <InspectionAppointment
    appointment={appointment}
    loading={loading}
    loadFailed={loadFailed}
    canReadChecklist={user?.privileges.includes('CHECKLIST_READ')}
  />
}
