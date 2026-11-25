import type { PreviousDefect, PreviousInspection } from './api'

export interface EarlierDefect {
  defect: PreviousDefect
  inspectedAt: string | null
}

/** Schlüssel über Bereich und Titel, weil Kriterien-IDs je Checklisten-Fassung wechseln. */
export function criterionKey(area: string, title: string): string {
  return `${area.trim().toLowerCase()}\u0000${title.trim().toLowerCase()}`
}

/**
 * Jüngster früherer Befund je Kriterium. `previous` kommt neueste zuerst vom Backend,
 * deshalb gewinnt der erste Treffer.
 */
export function earlierDefects(previous: PreviousInspection[]): Map<string, EarlierDefect> {
  const result = new Map<string, EarlierDefect>()
  for (const inspection of previous) {
    for (const defect of inspection.defects) {
      const key = criterionKey(defect.area, defect.criterionTitle)
      if (!result.has(key)) result.set(key, { defect, inspectedAt: inspection.inspectedAt })
    }
  }
  return result
}
