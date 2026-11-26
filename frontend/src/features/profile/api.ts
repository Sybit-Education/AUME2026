import { api } from '@/lib/auth/client'
import { requireOk } from '@/lib/auth/response'
import type { LoggedInUser } from '@/lib/auth/client'

export interface ProfileData {
  responsibilities: StaffResidence[]
}

export interface StaffResidence {
  id: number
  name: string
  kind: 'CARETAKER' | 'DEPUTY'
  flats: string[]
}

interface StaffResidencePage {
  content: StaffResidence[]
  page: { totalPages: number }
}

export async function loadProfile(user: LoggedInUser, signal: AbortSignal): Promise<ProfileData> {
  if (!user.roles.includes('CARETAKER')) return { responsibilities: [] }

  const responsibilities: StaffResidence[] = []
  let pageNumber = 0
  let totalPages = 1
  while (pageNumber < totalPages) {
    const response = requireOk(
      await api(`/api/residences/my-assignments?page=${pageNumber}&size=100`, { signal }),
      'errors.residences',
    )
    const page = (await response.json()) as StaffResidencePage
    responsibilities.push(...page.content)
    totalPages = page.page.totalPages
    pageNumber += 1
  }
  return { responsibilities }
}