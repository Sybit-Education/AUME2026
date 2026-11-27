import type { InspectionSummary } from './api'

export type StatusKey = 'READY' | 'ANNOUNCED' | 'DRAFT'

/**
 * Angezeigter Status nach dem, was man tun kann: in Bearbeitung (begonnen), startbereit (Zeitraum läuft oder
 * kein Zeitraum, z. B. Nachkontrolle) oder angekündigt (Zeitraum beginnt erst, starten ab dem ersten Tag).
 * Die Tabelle Anstehend und die Kacheln der Übersicht zählen danach. {@code day} ist der heutige Tag aus
 * {@code useToday()}, damit sich der Status über Mitternacht ändert.
 */
export function statusOf(inspection: InspectionSummary, day: string): StatusKey {
  if (inspection.status === 'DRAFT') return 'DRAFT'
  return inspection.startsOn && inspection.startsOn > day ? 'ANNOUNCED' : 'READY'
}
