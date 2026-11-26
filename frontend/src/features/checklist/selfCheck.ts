import { SessionExpired } from '@/lib/auth/response'
import { UiError } from '@/lib/errors'
import type { SelfCheck } from './api'

/** ready mit null: die Person gehört keiner WG an. */
export type SelfCheckState = { status: 'ready'; value: SelfCheck | null } | { status: 'failed' }

export async function loadSelfCheck(request: Promise<SelfCheck | null>): Promise<SelfCheckState> {
  try {
    return { status: 'ready', value: await request }
  } catch (cause) {
    // Abgelaufene Sitzung und fehlende Rechte gelten für die ganze Seite.
    if (cause instanceof Response || cause instanceof SessionExpired) throw cause
    if (cause instanceof UiError && cause.key === 'errors.sessionExpired') throw cause
    return { status: 'failed' }
  }
}
