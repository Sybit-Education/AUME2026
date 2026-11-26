import type { LoggedInUser } from '@/lib/auth/client'
import { SessionExpired } from '@/lib/auth/response'
import { UiError } from '@/lib/errors'
import { loadDashboard } from '@/features/dashboard/api'
import { getPlan, type Plan } from '@/features/putzplan/api'
import { getMembership, type Membership } from '@/features/wg/api'

/** Ein Bereich der Startseite, der für sich allein fehlschlagen darf. */
export type Section<T> = { status: 'ready'; value: T } | { status: 'failed' }

async function section<T>(request: Promise<T>): Promise<Section<T>> {
  try {
    return { status: 'ready', value: await request }
  } catch (cause) {
    // Abgelaufene Sitzung und fehlende Rechte gelten für die ganze Seite.
    if (cause instanceof Response || cause instanceof SessionExpired) throw cause
    if (cause instanceof UiError && cause.key === 'errors.sessionExpired') throw cause
    return { status: 'failed' }
  }
}

export interface HomeData {
  greeting: string | null
  /** Nur für Studierende mit WG-Funktionen, sonst null. */
  membership: Section<Membership | null> | null
  plan: Section<Plan | null> | null
}

/** Dashboard-Daten, für Studierende ergänzt um WG und Putzaufgaben der Woche (#99). */
export async function loadHome(user: LoggedInUser, signal: AbortSignal): Promise<HomeData> {
  const student = user.roles.includes('STUDENT')
  const resident = student && user.privileges.includes('WG_MEMBERSHIP')
  const [dashboard, membership, plan] = await Promise.all([
    // Studierende sehen keinen Gruß aus dem Backend, ihr Kopf zeigt Name und WG.
    student ? { greeting: null } : loadDashboard(user, signal),
    resident ? section(getMembership(signal)) : null,
    resident ? section(getPlan(null, signal)) : null,
  ])
  return { ...dashboard, membership, plan }
}
