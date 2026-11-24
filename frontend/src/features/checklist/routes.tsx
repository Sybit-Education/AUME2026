import { redirect, type RouteObject } from 'react-router'
import { protectedLoader } from '@/lib/auth/loaders'
import RequirePrivilege from '@/lib/auth/RequirePrivilege'
import ChecklistPage, { type ChecklistData } from './ChecklistPage'
import { getChecklist } from './api'
import './checklist.css'

export const checklistRoutes: RouteObject[] = [
  {
    path: 'checkliste',
    element: <RequirePrivilege privilege="CHECKLIST_READ"><ChecklistPage /></RequirePrivilege>,
    loader: protectedLoader('CHECKLIST_READ', async ({ request }, user): Promise<ChecklistData> => {
      if (!user.roles.includes('STUDENT')) throw redirect('/forbidden')
      return { checklist: await getChecklist(request.signal) }
    }),
  },
]
