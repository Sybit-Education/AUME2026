import { redirect, type RouteObject } from 'react-router'
import { protectedLoader } from '@/lib/auth/loaders'
import RequirePrivilege from '@/lib/auth/RequirePrivilege'
import ChecklistPage, { type ChecklistData } from './ChecklistPage'
import { getChecklist } from './api'
import './checklist.css'

// „Kontrolle“ führt vorerst direkt zur Checkliste. Termin, Ergebnis und Verlauf kommen als weitere
// Unterseiten von /kontrolle hinzu.
export const checklistRoutes: RouteObject[] = [
  { path: 'kontrolle', loader: () => redirect('/kontrolle/checkliste') },
  {
    path: 'kontrolle/checkliste',
    element: <RequirePrivilege privilege="CHECKLIST_READ"><ChecklistPage /></RequirePrivilege>,
    loader: protectedLoader('CHECKLIST_READ', async ({ request }): Promise<ChecklistData> => ({
      checklist: await getChecklist(request.signal),
    })),
  },
]
