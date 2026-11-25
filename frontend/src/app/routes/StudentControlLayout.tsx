import { useTranslation } from 'react-i18next'
import { Link, Navigate, NavLink, Outlet, useMatch } from 'react-router'
import { useAuth } from '@/lib/auth/AuthContext'

export default function StudentControlLayout() {
  const { t } = useTranslation()
  const { user } = useAuth()
  // „Ablauf und Konsequenzen“ ist über die Checkliste erreichbar; dort bleibt der Reiter „Checkliste“ markiert.
  const onChecklist = useMatch('/student/inspection/checklist') !== null
  const onProcessPage = useMatch('/student/inspection/process') !== null
  const checklistActive = onChecklist || onProcessPage
  if (!user?.roles.includes('STUDENT')) return <Navigate to="/forbidden" replace />

  return (
    <>
      <nav className="student-control-tabs" aria-label={t('studentControl.navigation')}>
        <NavLink to="appointment" end>{t('studentControl.appointment')}</NavLink>
        {user.privileges.includes('CHECKLIST_READ') && (
          <Link to="checklist" className={checklistActive ? 'active' : undefined} aria-current={checklistActive ? 'page' : undefined}>
            {t('studentControl.checklist')}
          </Link>
        )}
        <NavLink to="result" end>{t('studentControl.result')}</NavLink>
        <NavLink to="history" end>{t('studentControl.history')}</NavLink>
      </nav>
      <Outlet />
    </>
  )
}
