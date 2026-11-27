import { api } from '@/lib/auth/client'
import { requireOk } from '@/lib/auth/response'

const path = '/api/student/notifications'

export type StudentMessage = {
  id: number
  type: 'ANNOUNCEMENT' | 'REMINDER_7_DAYS' | 'REMINDER_1_DAY'
  createdAt: string
  occurredAt: string
  readAt: string | null
  flatLabel: string
  startsOn: string
  endsOn: string
  windowStart: string | null
  windowEnd: string | null
  hasChecklistPdf: boolean
  announcementKind: 'ANNOUNCEMENT' | 'RESCHEDULE' | null
  announcementMessage: string | null
  attachment: { fileName: string } | null
}

export type StudentMessages = {
  content: StudentMessage[]
  page: { size: number; number: number; totalElements: number; totalPages: number }
}

export async function getMessages(page: number, signal: AbortSignal): Promise<StudentMessages> {
  const response = await api(`${path}?page=${page}&size=20`, { signal })
  signal.throwIfAborted()
  return await requireOk(response, 'errors.studentMessages').json() as StudentMessages
}

export async function getUnreadCount(signal: AbortSignal): Promise<number> {
  const response = await api(`${path}/unread-count`, { signal })
  signal.throwIfAborted()
  const { count } = await requireOk(response, 'errors.studentMessages').json() as { count: number }
  return count
}

export async function markMessageRead(id: number, signal: AbortSignal): Promise<void> {
  const response = await api(`${path}/${id}/read`, { method: 'PUT', signal })
  signal.throwIfAborted()
  requireOk(response, 'errors.studentMessageRead')
}

const germanFileName = 'Checkliste_Sauberkeit.pdf'
const englishFileName = 'Cleanliness_Checklist.pdf'

export function checklistFileName(language?: string) {
  return language === 'en' ? englishFileName : germanFileName
}

export async function downloadMessageChecklist(id: number, language: string | undefined, signal: AbortSignal) {
  const response = await api(`${path}/${id}/checklist.pdf`, { signal })
  signal.throwIfAborted()
  requireOk(response, 'errors.studentMessageDownload')
  const headerName = /filename="?([^";]+)"?/i.exec(response.headers.get('Content-Disposition') ?? '')?.[1]
  const fileName = headerName === germanFileName || headerName === englishFileName
    ? headerName : checklistFileName(language)
  await saveDownload(response, fileName, signal)
}

export async function downloadMessageAttachment(id: number, fileName: string, signal: AbortSignal) {
  const response = await api(`${path}/${id}/attachment`, { signal })
  signal.throwIfAborted()
  requireOk(response, 'errors.studentAttachmentDownload')
  await saveDownload(response, fileName, signal)
}

async function saveDownload(response: Response, fileName: string, signal: AbortSignal) {
  const blob = await response.blob()
  signal.throwIfAborted()
  const url = URL.createObjectURL(blob)
  const revokeUrl = URL.revokeObjectURL.bind(URL)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.append(link)
  try {
    link.click()
  } finally {
    link.remove()
    // Give the browser time to start the download before releasing the URL.
    setTimeout(() => revokeUrl(url), 1000)
  }
}
