import { useState, type RefObject } from 'react'

/*
 * Zustand und Hilfsfunktionen der Kontroll-Tabellen; die Bausteine zum Anzeigen stehen in tableControls.tsx.
 */

export type SortDirection = 'asc' | 'desc'

/** Breite des Filter-Menüs; danach richtet sich seine Position. */
export const POPOVER_WIDTH = 250

/** Was das Filter-Menü einer Spalte anbietet. */
export type FilterSpec =
  | { kind: 'text'; label: string; value: string }
  | { kind: 'options'; label: string; value: string; options: [value: string, text: string][] }
  | { kind: 'dates'; label: string; from: string; to: string }

/** Ergebnis des Menüs: Text, gewählte Option oder Zeitraum (JJJJ-MM-TT); leer heißt „kein Filter“. */
export interface FilterValues {
  text?: string
  value?: string
  from?: string
  to?: string
}

interface OpenMenu<C extends string> {
  column: C
  left: number
  top: number
  button: HTMLButtonElement
}

/** Öffnet das Filter-Menü einer Spalte unter ihrem Trichter-Symbol, innerhalb von {@code section}. */
export function useFilterMenu<C extends string>(section: RefObject<HTMLElement | null>) {
  const [open, setOpen] = useState<OpenMenu<C> | null>(null)

  function close() {
    open?.button.focus()
    setOpen(null)
  }

  function toggle(column: C, button: HTMLButtonElement) {
    if (open?.column === column) {
      close()
      return
    }
    // Das Menü liegt außerhalb des scrollenden Tabellenrahmens, sonst würde es abgeschnitten.
    const box = section.current?.getBoundingClientRect()
    const anchor = button.getBoundingClientRect()
    if (!box) return
    const left = Math.max(8, Math.min(anchor.left - box.left, box.width - POPOVER_WIDTH - 8))
    setOpen({ column, left, top: anchor.bottom - box.top + 6, button })
  }

  return { open, toggle, close }
}

/** Sortierung und Filter einer Tabelle, die komplett im Browser liegt (Anfragen, Anstehend). */
export function useLocalTable<C extends string>() {
  const [sort, setSort] = useState<{ column: C; dir: SortDirection } | null>(null)
  const [filters, setFilters] = useState<Partial<Record<C, FilterValues>>>({})

  return {
    sort,
    filters,
    /** Gleiche Spalte dreht die Richtung, nach „absteigend“ zurück zur Standard-Reihenfolge. */
    toggleSort(column: C) {
      setSort((current) => current?.column !== column ? { column, dir: 'asc' }
        : current.dir === 'asc' ? { column, dir: 'desc' } : null)
    },
    sortedBy(column: C): SortDirection | null {
      return sort?.column === column ? sort.dir : null
    },
    setFilter(column: C, values: FilterValues) {
      setFilters((current) => {
        const next = { ...current }
        if (Object.values(values).some((value) => value)) next[column] = values
        else delete next[column]
        return next
      })
    },
    reset() {
      setFilters({})
    },
  }
}

/** Vergleich für Text, Zahl oder Datum; leere Werte stehen immer am Ende. */
export function compareValues(a: string | number | null, b: string | number | null, dir: SortDirection): number {
  if (a === b) return 0
  if (a === null || a === '') return 1
  if (b === null || b === '') return -1
  const order = typeof a === 'number' && typeof b === 'number' ? a - b : String(a).localeCompare(String(b), undefined, { numeric: true })
  return dir === 'asc' ? order : -order
}

/** Überschneidet sich der Zeitraum mit dem Filter von/bis (beide inklusive)? Ohne Zeitraum: nein. */
export function overlaps(start: string | null, end: string | null, filter: FilterValues | undefined): boolean {
  if (!filter || (!filter.from && !filter.to)) return true
  if (!start || !end) return false
  return (!filter.to || start <= filter.to) && (!filter.from || end >= filter.from)
}

export function contains(text: string, filter: FilterValues | undefined): boolean {
  return !filter?.text || text.toLowerCase().includes(filter.text.trim().toLowerCase())
}
