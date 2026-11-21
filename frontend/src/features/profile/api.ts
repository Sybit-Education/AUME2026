import { api } from '@/lib/auth/client'
import { requireOk } from '@/lib/auth/response'
import type { LoggedInUser } from '@/lib/auth/client'

export interface ProfileData {
  residences: string[]
}

interface ResidencePage {
  content: { name: string }[]
}

export async function loadProfile(user: LoggedInUser, signal: AbortSignal): Promise<ProfileData> {
  if (!user.roles.includes('CARETAKER')) return { residences: [] }

  const response = requireOk(
    await api('/api/residences?page=0&size=100', { signal }),
    'errors.residences',
  )
  const page = (await response.json()) as ResidencePage
  return { residences: page.content.map((residence) => residence.name) }
}