export function createInspectionCalendar(
  id: number,
  startsOn: string,
  endsOn: string,
  generatedAt: Date,
) {
  const dayAfterEnd = new Date(`${endsOn}T00:00:00Z`)
  dayAfterEnd.setUTCDate(dayAfterEnd.getUTCDate() + 1)

  return [
    'BEGIN:VCALENDAR',
    'VERSION:2.0',
    'PRODID:-//Seezeit//WG Inspection//EN',
    'BEGIN:VEVENT',
    `UID:inspection-${id}@seezeit`,
    `DTSTAMP:${generatedAt.toISOString().replace(/[-:]/g, '').replace(/\.\d{3}Z$/, 'Z')}`,
    `DTSTART;VALUE=DATE:${startsOn.replaceAll('-', '')}`,
    `DTEND;VALUE=DATE:${dayAfterEnd.toISOString().slice(0, 10).replaceAll('-', '')}`,
    'SUMMARY:Seezeit',
    'END:VEVENT',
    'END:VCALENDAR',
    '',
  ].join('\r\n')
}
