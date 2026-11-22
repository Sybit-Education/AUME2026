import type { LoaderFunctionArgs } from 'react-router'
import { loadCaretakerOverview } from './api'

/** Wer dieses Privileg hat, sieht auf der Startseite (`/`) die Übersicht der Objektbetreuung. */
export const CARETAKER_OVERVIEW_PRIVILEGE = 'INSPECTION_OVERVIEW_READ'

export function loadOverview({ request }: LoaderFunctionArgs) {
  return loadCaretakerOverview(request.signal)
}
