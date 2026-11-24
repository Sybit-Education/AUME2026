import type { Criterion, Finding, OverallResult } from './api'

export interface AreaState {
  area: string
  criteria: Criterion[]
  open: number
  deficient: number
}

/** Bereiche in der Reihenfolge der Checkliste, mit offenen und mangelhaften Kriterien. */
export function areas(criteria: Criterion[], findings: Finding[]): AreaState[] {
  const byCriterion = new Map(findings.map((finding) => [finding.criterionId, finding]))
  const result: AreaState[] = []
  for (const criterion of [...criteria].sort((a, b) => a.position - b.position)) {
    let state = result.find((entry) => entry.area === criterion.area)
    if (!state) {
      state = { area: criterion.area, criteria: [], open: 0, deficient: 0 }
      result.push(state)
    }
    state.criteria.push(criterion)
    const finding = byCriterion.get(criterion.id)
    if (!finding) state.open += 1
    else if (finding.rating === 'DEFICIENT') state.deficient += 1
  }
  return result
}

/** Vorschlag für das Gesamtergebnis; die Entscheidung trifft immer die prüfende Person. */
export function suggestedResult(findings: Finding[]): OverallResult {
  return findings.some((finding) => finding.rating === 'DEFICIENT') ? 'FAILED' : 'PASSED'
}

const icons: Record<string, string> = { Küche: '🍳', Boden: '🧹', Treppe: '🪜', Bad: '🚿' }

export function areaIcon(area: string): string {
  return icons[area] ?? '📋'
}
