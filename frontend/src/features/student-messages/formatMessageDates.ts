const locales = { de: 'de-DE', en: 'en-GB' }

function locale(language?: string) {
  return language === 'en' ? locales.en : locales.de
}

export function formatMessageDate(occurredAt: string, language?: string) {
  return new Intl.DateTimeFormat(locale(language), {
    day: '2-digit', month: '2-digit', year: 'numeric', timeZone: 'Europe/Berlin',
  }).format(new Date(occurredAt))
}

export function formatMessagePeriod(startsOn: string, endsOn: string, language?: string, withWeekday = false) {
  function format(date: string, withYear: boolean) {
    return new Intl.DateTimeFormat(locale(language), {
      weekday: withWeekday ? 'short' : undefined,
      day: '2-digit', month: language === 'en' ? 'short' : '2-digit', year: withYear ? 'numeric' : undefined,
      timeZone: 'UTC',
    }).format(new Date(`${date}T00:00:00Z`))
  }

  const end = format(endsOn, true)
  if (startsOn === endsOn) return end
  const start = format(startsOn, startsOn.slice(0, 4) !== endsOn.slice(0, 4))
  return language === 'en' ? `${start} to ${end}` : `${start} bis ${end}`
}
