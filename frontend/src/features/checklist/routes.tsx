import { redirect, type RouteObject } from 'react-router'
import { protectedLoader } from '@/lib/auth/loaders'
import RequirePrivilege from '@/lib/auth/RequirePrivilege'
import ChecklistPage, { type ChecklistData } from './ChecklistPage'
import { getChecklist, getSelfCheck } from './api'
import { selfCheckAction } from './actions'
import { loadSelfCheck } from './selfCheck'
import './checklist.css'

export const checklistRoutes: RouteObject[] = [
  {
    path: 'checklist',
    element: <RequirePrivilege privilege="CHECKLIST_READ"><ChecklistPage /></RequirePrivilege>,
    loader: protectedLoader('CHECKLIST_READ', async ({ request }, user): Promise<ChecklistData> => {
      if (!user.roles.includes('STUDENT')) throw redirect('/forbidden')
      const [checklist, selfCheck] = await Promise.all([
        getChecklist(request.signal),
        // Selbstcheck nur für WG-Mitglieder; schlägt er fehl, bleibt die Checkliste lesbar.
        user.privileges.includes('WG_MEMBERSHIP') ? loadSelfCheck(getSelfCheck(request.signal)) : null,
      ])
      return { checklist, selfCheck }
    }),
    action: selfCheckAction,
  },
]
