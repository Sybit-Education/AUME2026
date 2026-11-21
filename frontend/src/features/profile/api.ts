import { api } from '@/lib/auth/client'
import { requireOk } from '@/lib/auth/response'
import type { LoggedInUser } from '@/lib/auth/client'
import { UiError } from '@/lib/errors'
import { setOfflineModeEnabled } from '@/lib/offline'

export interface ProfileData {
  residences: string[]
}

interface ResidencePage {
  content: { name: string }[]
  page: { totalPages: number }
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

export async function prepareOfflineMode(user: LoggedInUser, signal: AbortSignal) {
  if (typeof navigator !== 'undefined' && !navigator.onLine) {
    throw new UiError('errors.offlineNeedsNetwork')
  }

  setOfflineModeEnabled(true)
  try {
    requireOk(await api('/api/me', { signal }), 'errors.profile')
    if (user.privileges.includes('GREETING_READ')) {
      requireOk(await api('/api/greeting', { signal }), 'errors.greeting')
    }
    if (user.privileges.includes('RESIDENCE_READ')) {
      const profileResponse = requireOk(
        await api('/api/residences?page=0&size=100', { signal }),
        'errors.residences',
      )
      await profileResponse.json()

      const firstPage = requireOk(
        await api('/api/residences?page=0&size=20', { signal }),
        'errors.residences',
      )
      const page = (await firstPage.json()) as ResidencePage
      if (!Number.isSafeInteger(page.page.totalPages) || page.page.totalPages < 1) {
        throw new UiError('errors.offlineSetup')
      }
      for (let pageNumber = 1; pageNumber < page.page.totalPages; pageNumber++) {
        requireOk(
          await api(`/api/residences?page=${pageNumber}&size=20`, { signal }),
          'errors.residences',
        )
      }
    }
  } catch (cause) {
    setOfflineModeEnabled(false)
    throw cause
  }
}