const DATE: Intl.DateTimeFormatOptions = { day: '2-digit', month: '2-digit', year: 'numeric', timeZone: 'UTC' }

/** Zeitraum zweier Kalendertage (JJJJ-MM-TT), z. B. „01.–03.12.2026“. */
export function formatPeriod(from: string, to: string, language: string | undefined): string {
  const day = (value: string) => new Date(`${value}T00:00:00Z`)
  return new Intl.DateTimeFormat(language ?? 'de', DATE).formatRange(day(from), day(to))
}

/** Heutiger Kalendertag (JJJJ-MM-TT) in der Zeitzone des Geräts. */
export function today(): string {
  const now = new Date()
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
}

/** Kurzes Datum ohne Jahr, z. B. „01.12.“. */
export function formatDayMonth(day: string, language: string | undefined): string {
  return new Intl.DateTimeFormat(language ?? 'de', { day: '2-digit', month: '2-digit', timeZone: 'UTC' })
    .format(new Date(`${day}T00:00:00Z`))
}

/** Uhrzeitfenster wie „09:00 – 16:00“, oder null, wenn eine Angabe fehlt. */
export function formatWindow(start: string | null, end: string | null): string | null {
  return start && end ? `${start.slice(0, 5)} – ${end.slice(0, 5)}` : null
}
