import { redirect, type RouteObject } from 'react-router'
import { protectedLoader } from '@/lib/auth/loaders'
import RequirePrivilege from '@/lib/auth/RequirePrivilege'
import InspectionProcessPage, { type InspectionProcessData } from './InspectionProcessPage'
import { getInspectionInfo } from './api'
import './inspection-process.css'

/** US-23: Ablauf und Konsequenzen, erreichbar über den Link unter der Checkliste. */
export const inspectionProcessRoutes: RouteObject[] = [
  {
    path: 'process',
    element: <RequirePrivilege privilege="INFO_PAGE_READ"><InspectionProcessPage /></RequirePrivilege>,
    loader: protectedLoader('INFO_PAGE_READ', async ({ request }, user): Promise<InspectionProcessData> => {
      if (!user.roles.includes('STUDENT')) throw redirect('/forbidden')
      return { info: await getInspectionInfo(request.signal) }
    }),
  },
]
