import { useEffect, useState } from 'react'
import { today } from './dates'

/**
 * Heutiger Tag, der sich auch auf einer offenen Seite aktualisiert: jede Minute und beim Zurückkehren auf die
 * Seite. Bleibt die Übersicht über Mitternacht offen, wechseln Status und Kacheln so von selbst.
 */
export function useToday(): string {
  const [day, setDay] = useState(today)
  useEffect(() => {
    const check = () => setDay(today())
    const timer = window.setInterval(check, 60_000)
    document.addEventListener('visibilitychange', check)
    window.addEventListener('focus', check)
    return () => {
      window.clearInterval(timer)
      document.removeEventListener('visibilitychange', check)
      window.removeEventListener('focus', check)
    }
  }, [])
  return day
}
