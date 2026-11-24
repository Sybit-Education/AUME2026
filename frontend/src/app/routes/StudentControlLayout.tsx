import { useTranslation } from 'react-i18next'
import { Navigate, NavLink, Outlet } from 'react-router'
import { useAuth } from '@/lib/auth/AuthContext'

export default function StudentControlLayout() {
  const { t } = useTranslation()
  const { user } = useAuth()
  if (!user?.roles.includes('STUDENT')) return <Navigate to="/forbidden" replace />

  return (
    <>
      <nav className="student-control-tabs" aria-label={t('studentControl.navigation')}>
        <NavLink to="termin" end>{t('studentControl.appointment')}</NavLink>
        {user.privileges.includes('CHECKLIST_READ') && (
          <NavLink to="checkliste" end>{t('studentControl.checklist')}</NavLink>
        )}
        <NavLink to="ergebnis" end>{t('studentControl.result')}</NavLink>
        <NavLink to="verlauf" end>{t('studentControl.history')}</NavLink>
      </nav>
      <Outlet />
    </>
  )
}
