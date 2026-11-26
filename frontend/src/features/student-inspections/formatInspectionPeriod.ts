const germanWeekdays = ['So', 'Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa']
const englishDate = new Intl.DateTimeFormat('en-GB', {
  weekday: 'short', day: 'numeric', month: 'short', timeZone: 'UTC',
})

export function formatInspectionPeriod(startsOn: string, endsOn: string, language: string | undefined): string {
  const english = language === 'en'
  const start = new Date(`${startsOn}T00:00:00Z`)
  const end = new Date(`${endsOn}T00:00:00Z`)

  function formatDate(date: Date, includeYear: boolean): string {
    const year = date.getUTCFullYear()
    if (english) {
      const parts = englishDate.formatToParts(date)
      const weekday = parts.find(({ type }) => type === 'weekday')?.value
      const day = parts.find(({ type }) => type === 'day')?.value
      const month = parts.find(({ type }) => type === 'month')?.value
      return `${weekday} ${day} ${month}${includeYear ? ` ${year}` : ''}`
    }

    const day = String(date.getUTCDate()).padStart(2, '0')
    const month = String(date.getUTCMonth() + 1).padStart(2, '0')
    return `${germanWeekdays[date.getUTCDay()]} ${day}.${month}.${includeYear ? year : ''}`
  }

  const formattedEnd = formatDate(end, true)
  if (startsOn === endsOn) return formattedEnd

  const formattedStart = formatDate(start, start.getUTCFullYear() !== end.getUTCFullYear())
  return `${formattedStart}${english ? ' to ' : ' bis '}${formattedEnd}`
}
