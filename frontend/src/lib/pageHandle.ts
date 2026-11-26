import type { ParseKeys } from 'i18next'

/**
 * Angaben einer Route für die Kopfleiste der Studierenden-App: `title` ist ein Übersetzungsschlüssel
 * (erhält `name` des angemeldeten Nutzers als Platzhalter), `back` die Adresse hinter dem Zurück-Pfeil.
 * Die tiefste Route mit `title` gewinnt.
 */
export interface PageHandle {
  title: ParseKeys
  back?: string
}

export function pageHandle(handle: PageHandle): PageHandle {
  return handle
}
