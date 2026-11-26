import { useLoaderData } from 'react-router'
import InspectionAppointment from './InspectionAppointment'
import type { loadAppointment } from './api'

export default function InspectionDetailsPage() {
  const data = useLoaderData<{ appointment: Awaited<ReturnType<typeof loadAppointment>> } | null>()
  return <InspectionAppointment appointment={data?.appointment ?? null} />
}
